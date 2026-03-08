/**
 *  Winix Air Purifier Driver (Parent Driver)
 *
 *  A Hubitat driver for Winix air purifiers.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

import groovy.transform.Field

metadata {
    definition(
        name: "Winix Air Purifier",
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/drivers/winix-purifier-driver.groovy"
    ) {
        // Primary capabilities (required for HomeKit integration)
        capability "Switch"
        capability "FanControl"      // Adds setSpeed - required for HomeKit Fan
        capability "SwitchLevel"     // Adds setLevel - required for HomeKit percentage slider
        
        // Secondary capabilities
        capability "Refresh"
        capability "Actuator"
        
        // Override FanControl default speeds
        attribute "supportedFanSpeeds", "JSON_OBJECT"
        
        // Air quality stored as attribute (not capability, to avoid HomeKit misclassification)
        attribute "airQuality", "enum", ["good", "fair", "poor"]
        
        // Custom attributes
        attribute "autoMode", "enum", ["on", "off"]
        attribute "plasmawave", "enum", ["on", "off"]
        attribute "sleepMode", "enum", ["on", "off"]
        attribute "filterLife", "number"
        attribute "filterChangeNeeded", "enum", ["yes", "no"]
        attribute "ambientLight", "number"
        attribute "airQualityIndex", "number"
        
        // Custom commands
        command "setFanSpeed", [[name: "speed", type: "ENUM", constraints: ["low", "medium", "high", "turbo", "auto"]]]
        command "setAutoMode", [[name: "mode", type: "ENUM", constraints: ["on", "off"]]]
        command "setPlasmawave", [[name: "mode", type: "ENUM", constraints: ["on", "off"]]]
        command "setSleepMode", [[name: "mode", type: "ENUM", constraints: ["on", "off"]]]
        command "configure"
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "autoRefresh", type: "bool", title: "Auto refresh status", defaultValue: true
        input name: "refreshInterval", type: "number", title: "Refresh interval (seconds)", defaultValue: 60, range: "30..600"
    }
}

// ==================== Winix Constants ====================
@Field static final String WINIX_API_BASE = "https://us.api.winix-iot.com"

// Device attribute codes
@Field static final String ATTR_POWER = "A02"
@Field static final String ATTR_MODE = "A03"
@Field static final String ATTR_AIRFLOW = "A04"
@Field static final String ATTR_PLASMAWAVE = "A07"
@Field static final String ATTR_AIR_QUALITY = "S07"
@Field static final String ATTR_AMBIENT_LIGHT = "S14"
@Field static final String ATTR_FILTER_HOURS = "A21"

// Power values
@Field static final String POWER_ON = "1"
@Field static final String POWER_OFF = "0"

// Mode values
@Field static final String MODE_AUTO = "01"
@Field static final String MODE_MANUAL = "02"

// Airflow values: 01=Low, 02=Medium, 03=High, 05=Turbo, 06=Sleep (no 04)
@Field static final String AIRFLOW_LOW = "01"
@Field static final String AIRFLOW_MEDIUM = "02"
@Field static final String AIRFLOW_HIGH = "03"
@Field static final String AIRFLOW_TURBO = "05"
@Field static final String AIRFLOW_SLEEP = "06"

// Plasmawave values
@Field static final String PLASMAWAVE_ON = "1"
@Field static final String PLASMAWAVE_OFF = "0"

// Air quality values
@Field static final String AIR_QUALITY_GOOD = "01"
@Field static final String AIR_QUALITY_FAIR = "02"
@Field static final String AIR_QUALITY_POOR = "03"

// Maximum filter hours (Winix stops counting at 6480)
@Field static final int MAX_FILTER_HOURS = 6480

// Speed mappings (using literal values since static fields can't reference each other)
// Airflow values: 01=Low, 02=Medium, 03=High, 05=Turbo, 06=Sleep (no 04)
@Field static final Map SPEED_TO_AIRFLOW = [
    "low": "01",        // AIRFLOW_LOW
    "medium": "02",     // AIRFLOW_MEDIUM
    "high": "03",       // AIRFLOW_HIGH
    "turbo": "05",      // AIRFLOW_TURBO
    "sleep": "06",      // AIRFLOW_SLEEP
    "auto": null,       // Auto mode handled separately
    "off": null,
    "on": "01"          // AIRFLOW_LOW
]

@Field static final Map AIRFLOW_TO_SPEED = [
    "01": "low",    // AIRFLOW_LOW
    "02": "medium", // AIRFLOW_MEDIUM
    "03": "high",   // AIRFLOW_HIGH
    "05": "turbo",  // AIRFLOW_TURBO
    "06": "sleep"   // AIRFLOW_SLEEP
]

// ==================== Lifecycle ====================

def installed() {
    logDebug "Installed"
    // Set initial default values for HomeKit
    sendEvent(name: "switch", value: "off", isStateChange: true)
    sendEvent(name: "level", value: 0, isStateChange: true)
    sendEvent(name: "speed", value: "off", isStateChange: true)
    initialize()
}

def updated() {
    logDebug "Updated"
    initialize()
}

def initialize() {
    logDebug "Initializing"
    unschedule()
    
    // Set supported fan speeds for FanControl capability (must be JSON array string)
    sendEvent(name: "supportedFanSpeeds", value: groovy.json.JsonOutput.toJson(["low", "medium", "high", "turbo", "auto"]))
    
    if (autoRefresh) {
        def interval = refreshInterval ?: 60
        // Schedule refresh at the specified interval (in seconds)
        if (interval <= 60) {
            runEvery1Minute(refresh)
        } else if (interval <= 300) {
            runEvery5Minutes(refresh)
        } else {
            runEvery10Minutes(refresh)
        }
    }
    
    // Create/remove child devices based on parent app settings
    manageChildDevices()
    
    // Initial refresh
    runIn(2, refresh)
}

def configure() {
    log.info "Configuring device - setting supported fan speeds"
    // Force set supported fan speeds
    def speeds = ["low", "medium", "high", "turbo", "auto"]
    sendEvent(name: "supportedFanSpeeds", value: groovy.json.JsonOutput.toJson(speeds))
    log.info "Set supportedFanSpeeds to: ${speeds}"
    initialize()
    // Force immediate refresh to sync state with HomeKit
    refresh()
}

def manageChildDevices() {
    def app = parent
    if (!app) return
    
    // Air Quality sensor
    if (app.getSetting("exposeAirQuality")) {
        createChildAirQuality()
    } else {
        removeChildAirQuality()
    }
    
    // Plasmawave switch
    if (app.getSetting("exposePlasmawave")) {
        createChildSwitch("plasmawave", "Plasmawave")
    } else {
        removeChildSwitch("plasmawave")
    }
    
    // Auto Mode switch
    if (app.getSetting("exposeAutoSwitch")) {
        createChildSwitch("autoMode", "Auto Mode")
    } else {
        removeChildSwitch("autoMode")
    }
    
    // Sleep Mode switch
    if (app.getSetting("exposeSleepSwitch")) {
        createChildSwitch("sleepMode", "Sleep Mode")
    } else {
        removeChildSwitch("sleepMode")
    }
    
    // Light Sensor
    if (app.getSetting("exposeAmbientLight")) {
        createChildLightSensor()
    } else {
        removeChildLightSensor()
    }
}

def createChildAirQuality() {
    def dni = "${device.deviceNetworkId}_airQuality"
    def existingChild = getChildDevice(dni)
    
    if (!existingChild) {
        logDebug "Creating child air quality sensor"
        try {
            addChildDevice(
                "rbyrbt",
                "Winix Air Quality Sensor",
                dni,
                [
                    label: "${device.label} Air Quality",
                    name: "Winix Air Quality Sensor",
                    isComponent: false
                ]
            )
        } catch (Exception e) {
            log.error "Failed to create child air quality sensor: ${e.message}"
        }
    }
}

def removeChildAirQuality() {
    def dni = "${device.deviceNetworkId}_airQuality"
    def existingChild = getChildDevice(dni)
    
    if (existingChild) {
        logDebug "Removing child air quality sensor"
        try {
            deleteChildDevice(dni)
        } catch (Exception e) {
            log.warn "Failed to remove child air quality sensor: ${e.message}"
        }
    }
}

def createChildSwitch(String switchType, String label) {
    def dni = "${device.deviceNetworkId}_${switchType}"
    def existingChild = getChildDevice(dni)
    
    if (!existingChild) {
        logDebug "Creating child switch: ${label}"
        try {
            def child = addChildDevice(
                "rbyrbt",
                "Winix Child Switch",
                dni,
                [
                    label: "${device.label} ${label}",
                    name: "Winix ${label} Switch",
                    isComponent: false
                ]
            )
            child.updateDataValue("switchType", switchType)
        } catch (Exception e) {
            log.error "Failed to create child switch ${label}: ${e.message}"
        }
    }
}

def removeChildSwitch(String switchType) {
    def dni = "${device.deviceNetworkId}_${switchType}"
    def existingChild = getChildDevice(dni)
    
    if (existingChild) {
        logDebug "Removing child switch: ${switchType}"
        try {
            deleteChildDevice(dni)
        } catch (Exception e) {
            log.warn "Failed to remove child switch: ${e.message}"
        }
    }
}

def createChildLightSensor() {
    def dni = "${device.deviceNetworkId}_lightSensor"
    def existingChild = getChildDevice(dni)
    
    if (!existingChild) {
        logDebug "Creating child light sensor"
        try {
            addChildDevice(
                "rbyrbt",
                "Winix Light Sensor",
                dni,
                [
                    label: "${device.label} Light Sensor",
                    name: "Winix Light Sensor",
                    isComponent: false
                ]
            )
        } catch (Exception e) {
            log.error "Failed to create child light sensor: ${e.message}"
        }
    }
}

def removeChildLightSensor() {
    def dni = "${device.deviceNetworkId}_lightSensor"
    def existingChild = getChildDevice(dni)
    
    if (existingChild) {
        logDebug "Removing child light sensor"
        try {
            deleteChildDevice(dni)
        } catch (Exception e) {
            log.warn "Failed to remove child light sensor: ${e.message}"
        }
    }
}

// ==================== Capabilities ====================

def on() {
    logDebug "Turning on"
    setDeviceAttribute("A02", "1")  // ATTR_POWER, POWER_ON
    sendEvent(name: "switch", value: "on", isStateChange: true)
    
    // Set level based on current speed, default to low (25%) if unknown
    def currentSpeed = device.currentValue("speed") ?: "low"
    sendEvent(name: "level", value: speedToLevel(currentSpeed), isStateChange: true)
    
    // Default to auto mode when turning on
    runIn(1, setAutoModeOn)
}

def off() {
    logDebug "Turning off"
    setDeviceAttribute("A02", "0")  // ATTR_POWER, POWER_OFF
    sendEvent(name: "switch", value: "off", isStateChange: true)
    sendEvent(name: "speed", value: "off", isStateChange: true)
    sendEvent(name: "level", value: 0, isStateChange: true)
}

// Custom fan speed command with clean dropdown
def setFanSpeed(String speed) {
    logDebug "setFanSpeed: ${speed}"
    if (speed == "auto") {
        setAutoMode("on")
    } else {
        setAutoMode("off")
        setSpeed(speed)
    }
}

def setSpeed(String speed) {
    logDebug "Setting speed to ${speed}"
    
    if (speed == "auto" || speed == "on") {
        // Set to auto mode
        setAutoMode("on")
        return
    }
    
    if (speed == "off") {
        off()
        return
    }
    
    // Ensure device is on and in manual mode
    ensureOnAndManual()
    
    // Map speed to airflow value (using literal values to avoid static field issues)
    // Airflow values: 01=Low, 02=Medium, 03=High, 05=Turbo, 06=Sleep (no 04)
    // Note: medium-low and medium-high are mapped for FanControl compatibility
    String airflow
    String actualSpeed = speed
    switch(speed) {
        case "low":
            airflow = "01"
            break
        case "medium-low":
            airflow = "01"  // Map to low
            actualSpeed = "low"
            break
        case "medium":
            airflow = "02"
            break
        case "medium-high":
            airflow = "03"  // Map to high
            actualSpeed = "high"
            break
        case "high":
            airflow = "03"
            break
        case "turbo":
            airflow = "05"
            break
        case "sleep":
            airflow = "06"
            break
        case "on":
            airflow = "01"  // Default to low when "on" selected
            actualSpeed = "low"
            break
        default:
            log.warn "Unknown speed: ${speed}"
            return
    }
    
    logDebug "Setting airflow to ${airflow} for speed ${actualSpeed}"
    def success = setDeviceAttribute("A04", airflow)  // ATTR_AIRFLOW
    if (success) {
        def level = speedToLevel(actualSpeed)
        sendEvent(name: "speed", value: actualSpeed)
        sendEvent(name: "level", value: level, isStateChange: true)
        // Auto mode is off when manually setting speed
        sendEvent(name: "autoMode", value: "off")
        updateChildSwitch("autoMode", "off")
        // Sleep mode is off unless speed is "sleep"
        if (actualSpeed != "sleep") {
            sendEvent(name: "sleepMode", value: "off")
            updateChildSwitch("sleepMode", "off")
        }
    }
}

def cycleSpeed() {
    def currentSpeed = device.currentValue("speed")
    // Cycle through manual speeds only (auto is a separate mode)
    def speeds = ["low", "medium", "high", "turbo"]
    def currentIndex = speeds.indexOf(currentSpeed)
    // If current speed not in list (e.g., "auto"), start at low
    if (currentIndex < 0) currentIndex = -1
    def nextIndex = (currentIndex + 1) % speeds.size()
    setSpeed(speeds[nextIndex])
}

// SwitchLevel capability - maps percentage to fan speeds
// 0% = off, 1-25% = low, 26-50% = medium, 51-75% = high, 76-100% = turbo
def setLevel(level, duration = 0) {
    logDebug "setLevel: ${level}%"
    
    if (level <= 0) {
        off()
        return
    }
    
    String speed
    if (level <= 25) {
        speed = "low"
    } else if (level <= 50) {
        speed = "medium"
    } else if (level <= 75) {
        speed = "high"
    } else {
        speed = "turbo"
    }
    
    setSpeed(speed)
}

// Convert speed to level percentage
def speedToLevel(String speed) {
    switch(speed) {
        case "low": return 25
        case "medium": return 50
        case "high": return 75
        case "turbo": return 100
        case "sleep": return 25
        default: return 0
    }
}

def refresh() {
    logDebug "Refreshing status"
    
    def deviceId = device.getDataValue("deviceId")
    if (!deviceId) {
        log.error "Device ID not set"
        return
    }
    
    def status = getDeviceStatus(deviceId)
    if (status) {
        updateDeviceState(status)
    }
}

// ==================== Custom Commands ====================

def setAutoMode(String mode) {
    logDebug "Setting auto mode to ${mode}"
    
    if (mode == "on") {
        setAutoModeOn()
    } else {
        setAutoModeOff()
    }
}

def setAutoModeOn() {
    ensureOn()
    setDeviceAttribute("A03", "01")  // ATTR_MODE, MODE_AUTO
    sendEvent(name: "autoMode", value: "on")
    // Use "low" speed for HomeKit compatibility (HomeKit doesn't understand "auto" speed)
    sendEvent(name: "speed", value: "low")
    sendEvent(name: "level", value: 25, isStateChange: true)
    // Sleep mode is off in auto mode
    sendEvent(name: "sleepMode", value: "off")
    // Update child switches
    updateChildSwitch("autoMode", "on")
    updateChildSwitch("sleepMode", "off")
}

def setAutoModeOff() {
    ensureOn()
    setDeviceAttribute("A03", "02")  // ATTR_MODE, MODE_MANUAL
    sendEvent(name: "autoMode", value: "off")
    // Default to low speed in manual mode
    sendEvent(name: "speed", value: "low")
    sendEvent(name: "level", value: 25, isStateChange: true)
    // Update child switch
    updateChildSwitch("autoMode", "off")
}

def setPlasmawave(String mode) {
    logDebug "Setting plasmawave to ${mode}"
    
    ensureOn()
    
    String value = (mode == "on") ? "1" : "0"  // PLASMAWAVE_ON : PLASMAWAVE_OFF
    setDeviceAttribute("A07", value)  // ATTR_PLASMAWAVE
    sendEvent(name: "plasmawave", value: mode)
    // Update child switch
    updateChildSwitch("plasmawave", mode)
}

def setSleepMode(String mode) {
    logDebug "Setting sleep mode to ${mode}"
    
    if (mode == "on") {
        ensureOnAndManual()
        def success = setDeviceAttribute("A04", "06")  // ATTR_AIRFLOW = "A04", AIRFLOW_SLEEP = "06"
        if (success) {
            sendEvent(name: "speed", value: "sleep")
            sendEvent(name: "sleepMode", value: "on")
            sendEvent(name: "level", value: 25, isStateChange: true)
            // Auto mode is off when sleep mode is on
            sendEvent(name: "autoMode", value: "off")
            // Update child switches
            updateChildSwitch("sleepMode", "on")
            updateChildSwitch("autoMode", "off")
        }
    } else {
        // Turn off sleep mode - set to low speed
        ensureOnAndManual()
        def success = setDeviceAttribute("A04", "01")  // ATTR_AIRFLOW = "A04", AIRFLOW_LOW = "01"
        if (success) {
            sendEvent(name: "speed", value: "low")
            sendEvent(name: "sleepMode", value: "off")
            sendEvent(name: "level", value: 25, isStateChange: true)
            // Update child switch
            updateChildSwitch("sleepMode", "off")
        }
    }
}

// ==================== Helper Methods ====================

def ensureOn() {
    if (device.currentValue("switch") != "on") {
        logDebug "Turning on device first"
        setDeviceAttribute("A02", "1")  // ATTR_POWER, POWER_ON
        sendEvent(name: "switch", value: "on", isStateChange: true)
        def currentLevel = device.currentValue("level") ?: 25
        if (currentLevel == 0) currentLevel = 25
        sendEvent(name: "level", value: currentLevel, isStateChange: true)
        pauseExecution(500)
    }
}

def ensureOnAndManual() {
    ensureOn()
    
    if (device.currentValue("autoMode") == "on") {
        logDebug "Switching to manual mode"
        setDeviceAttribute("A03", "02")  // ATTR_MODE, MODE_MANUAL
        sendEvent(name: "autoMode", value: "off")
        pauseExecution(500)
    }
}

def updateDeviceState(Map status) {
    logDebug "Updating device state: ${status}"
    
    // Power / Switch
    def power = status["A02"]  // ATTR_POWER
    def isOn = power == "1"  // POWER_ON
    sendEvent(name: "switch", value: isOn ? "on" : "off", isStateChange: true)
    
    // Airflow / Speed (01=Low, 02=Medium, 03=High, 05=Turbo, 06=Sleep)
    def airflow = status["A04"]  // ATTR_AIRFLOW
    def speed = AIRFLOW_TO_SPEED[airflow] ?: "low"
    def isSleepMode = airflow == "06"  // AIRFLOW_SLEEP = 06
    
    // Mode - if sleep mode is active, auto mode is off
    def mode = status["A03"]  // ATTR_MODE
    def autoModeState = (mode == "01" && !isSleepMode) ? "on" : "off"  // MODE_AUTO, but not if sleeping
    sendEvent(name: "autoMode", value: autoModeState)
    // For HomeKit compatibility, use "low" instead of "auto" (HomeKit doesn't map "auto" to a level)
    if (mode == "01") {  // MODE_AUTO
        speed = "low"  // Auto mode shows as low speed for HomeKit
    }
    // When device is off, set speed to "off" and level to 0 (for HomeKit)
    if (!isOn) {
        speed = "off"
    }
    def level = isOn ? speedToLevel(speed) : 0
    sendEvent(name: "speed", value: speed, isStateChange: true)
    sendEvent(name: "level", value: level, isStateChange: true)
    sendEvent(name: "sleepMode", value: isSleepMode ? "on" : "off")
    
    // Plasmawave
    def plasmawave = status["A07"]  // ATTR_PLASMAWAVE
    def plasmawaveState = plasmawave == "1" ? "on" : "off"  // PLASMAWAVE_ON
    
    // Air Quality
    def airQuality = status["S07"]  // ATTR_AIR_QUALITY
    def aqValue = parseAirQuality(airQuality)
    sendEvent(name: "airQuality", value: aqValue.level)
    sendEvent(name: "airQualityIndex", value: aqValue.index)
    
    // Update child air quality sensor
    updateChildAirQuality(aqValue.index, aqValue.level)
    
    // Ambient Light
    def ambientLight = status["S14"]  // ATTR_AMBIENT_LIGHT
    if (ambientLight) {
        def lightValue = ambientLight.toInteger()
        sendEvent(name: "ambientLight", value: lightValue)
        updateChildLightSensor(lightValue)
    }
    
    // Filter Life
    def filterHours = status["A21"]  // ATTR_FILTER_HOURS
    if (filterHours) {
        def hours = filterHours.toInteger()
        def remainingHours = Math.max(6480 - hours, 0)  // MAX_FILTER_HOURS = 6480
        def remainingPercent = Math.round((remainingHours / 6480) * 100)  // MAX_FILTER_HOURS
        sendEvent(name: "filterLife", value: remainingPercent)
        
        // Check if filter change needed (threshold from parent app settings)
        def threshold = parent?.getSetting("filterReplacementThreshold") ?: 10
        def changeNeeded = remainingPercent <= threshold ? "yes" : "no"
        sendEvent(name: "filterChangeNeeded", value: changeNeeded)
    }
    
    // Update child switches
    updateChildSwitch("plasmawave", plasmawaveState)
    updateChildSwitch("autoMode", autoModeState)
    updateChildSwitch("sleepMode", isSleepMode ? "on" : "off")
}

def updateChildSwitch(String switchType, String state) {
    def dni = "${device.deviceNetworkId}_${switchType}"
    def child = getChildDevice(dni)
    if (child) {
        child.updateState(state)
    }
}

def updateChildAirQuality(Integer index, String level) {
    def dni = "${device.deviceNetworkId}_airQuality"
    def child = getChildDevice(dni)
    if (child) {
        child.updateAirQuality(index, level)
    }
}

def updateChildLightSensor(Integer value) {
    def dni = "${device.deviceNetworkId}_lightSensor"
    def child = getChildDevice(dni)
    if (child) {
        child.updateIlluminance(value)
    }
}

def parseAirQuality(String value) {
    // Handle both string codes and numeric values
    switch(value) {
        case "01":  // AIR_QUALITY_GOOD
        case "Good":
            return [level: "good", index: 1]
        case "02":  // AIR_QUALITY_FAIR
        case "Fair":
            return [level: "fair", index: 2]
        case "03":  // AIR_QUALITY_POOR
        case "Poor":
            return [level: "poor", index: 3]
        default:
            // Try to parse as a number (some devices return numeric values)
            try {
                def numValue = value.toFloat()
                if (numValue >= 2.1) return [level: "poor", index: 3]
                if (numValue >= 1.1) return [level: "fair", index: 2]
                return [level: "good", index: 1]
            } catch (Exception e) {
                return [level: "unknown", index: 0]
            }
    }
}

// ==================== API Calls ====================

def getDeviceStatus(String deviceId) {
    logDebug "Getting device status for ${deviceId}"
    
    def result = parent?.apiGet("/common/event/sttus/devices/${deviceId}")
    
    if (result?.body?.data && result.body.data.size() > 0) {
        return result.body.data[0].attributes
    }
    
    return null
}

def setDeviceAttribute(String attribute, String value) {
    def deviceId = device.getDataValue("deviceId")
    if (!deviceId) {
        log.error "Device ID not set"
        return false
    }
    
    logDebug "Setting ${attribute} to ${value} for device ${deviceId}"
    
    // The Winix API uses GET requests for control commands
    def path = "/common/control/devices/${deviceId}/A211/${attribute}:${value}"
    def result = parent?.apiGet(path)
    
    // Check for success - API returns "success" or "control success"
    def resultMsg = result?.headers?.resultMessage?.toString()?.toLowerCase()
    if (resultMsg?.contains("success")) {
        logDebug "Successfully set ${attribute} to ${value}"
        return true
    } else {
        log.warn "Failed to set ${attribute}: ${result}"
        return false
    }
}

// ==================== Logging ====================

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}
