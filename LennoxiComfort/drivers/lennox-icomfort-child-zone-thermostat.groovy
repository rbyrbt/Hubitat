/**
 *  Lennox iComfort Child Zone Thermostat (Child Driver)
 *
 *  Child device representing a Lennox thermostat zone. Created and managed by the parent Lennox iComfort Driver.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

import groovy.transform.Field

@Field static final String VERSION = "1.0.0"
@Field static final String DRIVER_NAME = "Lennox iComfort Child Zone Thermostat"

// HVAC mode mappings
@Field static final Map HVAC_MODE_MAP = [
    "off": "off",
    "cool": "cool",
    "heat": "heat",
    "heat and cool": "auto",
    "emergency heat": "emergency heat"
]

@Field static final Map HVAC_MODE_REVERSE_MAP = [
    "off": "off",
    "cool": "cool",
    "heat": "heat",
    "auto": "heat and cool",
    "emergency heat": "emergency heat"
]

// Fan mode mappings
@Field static final List HUBITAT_FAN_MODES = ["auto", "on", "circulate"]
@Field static final List LENNOX_FAN_MODES = ["auto", "on", "circulate"]

// Operating state mappings
@Field static final Map OPERATING_STATE_MAP = [
    "off": "idle",
    "heating": "heating",
    "cooling": "cooling"
]

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-child-zone-thermostat.groovy"
    ) {
        capability "Thermostat"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatCoolingSetpoint"
        capability "ThermostatSetpoint"
        capability "ThermostatMode"
        capability "ThermostatFanMode"
        capability "ThermostatOperatingState"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Refresh"
        
        // Standard thermostat attributes are provided by capabilities
        
        // Additional Lennox-specific attributes
        attribute "humidityMode", "string"
        attribute "humidifySetpoint", "number"
        attribute "dehumidifySetpoint", "number"
        attribute "humidityOperation", "string"
        attribute "damper", "number"
        attribute "demand", "number"
        attribute "scheduleId", "number"
        attribute "scheduleName", "string"
        attribute "overrideActive", "string"
        attribute "zoneEnabled", "string"
        attribute "allergenDefender", "string"
        attribute "ventilation", "string"
        attribute "singleSetpointMode", "string"
        
        // Lennox-specific commands
        command "setHumidityMode", [[name: "mode", type: "ENUM", constraints: ["off", "humidify", "dehumidify", "both"]]]
        command "setHumidifySetpoint", [[name: "setpoint", type: "NUMBER", description: "Humidity setpoint %"]]
        command "setDehumidifySetpoint", [[name: "setpoint", type: "NUMBER", description: "Dehumidity setpoint %"]]
        command "setSchedule", [[name: "scheduleId", type: "NUMBER", description: "Schedule ID"]]
        command "resumeSchedule"
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

void installed() {
    log.info "${DRIVER_NAME} installed"
    initialize()
}

void updated() {
    log.info "${DRIVER_NAME} updated"
    if (logEnable) runIn(86400, "logsOff")
    initialize()
}

void initialize() {
    // Set default supported modes
    sendEvent(name: "supportedThermostatModes", value: ["off", "heat", "cool", "auto", "emergency heat"])
    sendEvent(name: "supportedThermostatFanModes", value: HUBITAT_FAN_MODES)
}

void refresh() {
    parent?.refresh()
}

// Called by parent to update zone state
void updateZoneState(Map zoneState, Boolean singleSetpointMode) {
    if (logEnable) log.debug "Updating zone state: ${zoneState}"
    
    // Temperature (use numeric comparison so string "74" vs number 74 doesn't skip update)
    if (zoneState.temperature != null) {
        def temp = convertTemp(zoneState.temperature, zoneState.temperatureC)
        if (!valuesEqualAsNumber(device.currentValue("temperature"), temp)) {
            sendEvent(name: "temperature", value: temp, unit: getTemperatureScale(), descriptionText: "${device.displayName} temperature is ${temp}°${getTemperatureScale()}")
        }
    }
    
    // Humidity
    if (zoneState.humidity != null) {
        if (!valuesEqualAsNumber(device.currentValue("humidity"), zoneState.humidity)) {
            sendEvent(name: "humidity", value: zoneState.humidity, unit: "%", descriptionText: "${device.displayName} humidity is ${zoneState.humidity}%")
        }
    }
    
    // HVAC Mode (always send when present so UI shows it)
    if (zoneState.systemMode) {
        String hubitatMode = HVAC_MODE_MAP[zoneState.systemMode] ?: zoneState.systemMode
        sendEvent(name: "thermostatMode", value: hubitatMode, descriptionText: "${device.displayName} mode is ${hubitatMode}")
    }
    
    // Fan Mode (always send when present so UI shows it)
    if (zoneState.fanMode) {
        sendEvent(name: "thermostatFanMode", value: zoneState.fanMode, descriptionText: "${device.displayName} fan mode is ${zoneState.fanMode}")
    }
    
    // Operating State
    if (zoneState.tempOperation) {
        String opState = OPERATING_STATE_MAP[zoneState.tempOperation] ?: "idle"
        if (device.currentValue("thermostatOperatingState") != opState) {
            sendEvent(name: "thermostatOperatingState", value: opState, descriptionText: "${device.displayName} is ${opState}")
        }
    }
    
    // Setpoints
    String spmValue = singleSetpointMode.toString()
    if (device.currentValue("singleSetpointMode") != spmValue) {
        sendEvent(name: "singleSetpointMode", value: spmValue)
    }
    
    if (singleSetpointMode) {
        if (zoneState.sp != null) {
            def sp = convertTemp(zoneState.sp, zoneState.spC)
            sendEvent(name: "thermostatSetpoint", value: sp, unit: getTemperatureScale())
            sendEvent(name: "heatingSetpoint", value: sp, unit: getTemperatureScale())
            sendEvent(name: "coolingSetpoint", value: sp, unit: getTemperatureScale())
        }
    } else {
        // Always send setpoints when present so UI shows them (avoids type/scale comparison skips)
        if (zoneState.hsp != null) {
            def hsp = convertTemp(zoneState.hsp, zoneState.hspC)
            sendEvent(name: "heatingSetpoint", value: hsp, unit: getTemperatureScale(), descriptionText: "${device.displayName} heating setpoint is ${hsp}°${getTemperatureScale()}")
        }
        if (zoneState.csp != null) {
            def csp = convertTemp(zoneState.csp, zoneState.cspC)
            sendEvent(name: "coolingSetpoint", value: csp, unit: getTemperatureScale(), descriptionText: "${device.displayName} cooling setpoint is ${csp}°${getTemperatureScale()}")
        }
        // Set thermostatSetpoint based on current mode
        updateThermostatSetpoint()
    }
    
    // Humidity Mode and Setpoints
    if (zoneState.humidityMode) {
        sendEvent(name: "humidityMode", value: zoneState.humidityMode)
    }
    if (zoneState.husp != null) {
        sendEvent(name: "humidifySetpoint", value: zoneState.husp, unit: "%")
    }
    if (zoneState.desp != null) {
        sendEvent(name: "dehumidifySetpoint", value: zoneState.desp, unit: "%")
    }
    if (zoneState.humOperation) {
        sendEvent(name: "humidityOperation", value: zoneState.humOperation)
    }
    
    // Additional attributes
    if (zoneState.damper != null) {
        sendEvent(name: "damper", value: zoneState.damper, unit: "%")
    }
    if (zoneState.demand != null) {
        sendEvent(name: "demand", value: zoneState.demand)
    }
    if (zoneState.scheduleId != null) {
        sendEvent(name: "scheduleId", value: zoneState.scheduleId)
    }
    if (zoneState.scheduleName) {
        if (device.currentValue("scheduleName") != zoneState.scheduleName) {
            sendEvent(name: "scheduleName", value: zoneState.scheduleName)
        }
    }
    if (zoneState.allergenDefender != null) {
        sendEvent(name: "allergenDefender", value: zoneState.allergenDefender.toString())
    }
    if (zoneState.ventilation != null) {
        sendEvent(name: "ventilation", value: zoneState.ventilation.toString())
    }
    
    // Update supported modes based on zone capabilities
    updateSupportedModes(zoneState)
    
    // Store min/max for validation
    state.minHsp = zoneState.minHsp
    state.maxHsp = zoneState.maxHsp
    state.minCsp = zoneState.minCsp
    state.maxCsp = zoneState.maxCsp
    state.minHumSp = zoneState.minHumSp
    state.maxHumSp = zoneState.maxHumSp
    state.minDehumSp = zoneState.minDehumSp
    state.maxDehumSp = zoneState.maxDehumSp
}

void updateSupportedModes(Map zoneState) {
    List modes = ["off"]
    if (zoneState.heatingOption) modes.add("heat")
    if (zoneState.coolingOption) modes.add("cool")
    if (zoneState.heatingOption && zoneState.coolingOption) modes.add("auto")
    if (zoneState.emergencyHeatingOption) modes.add("emergency heat")
    
    sendEvent(name: "supportedThermostatModes", value: modes)
}

void updateThermostatSetpoint() {
    String mode = device.currentValue("thermostatMode")
    def setpoint = null
    
    switch (mode) {
        case "heat":
        case "emergency heat":
            setpoint = device.currentValue("heatingSetpoint")
            break
        case "cool":
            setpoint = device.currentValue("coolingSetpoint")
            break
        case "auto":
            // In auto mode, use the average or heating setpoint
            def hsp = device.currentValue("heatingSetpoint")
            def csp = device.currentValue("coolingSetpoint")
            setpoint = hsp  // Or could use (hsp + csp) / 2
            break
    }
    
    if (setpoint != null) {
        sendEvent(name: "thermostatSetpoint", value: setpoint, unit: getTemperatureScale())
    }
}

// Thermostat capability commands
void setThermostatMode(String mode) {
    if (logEnable) log.debug "setThermostatMode: ${mode}"
    
    String lennoxMode = HVAC_MODE_REVERSE_MAP[mode] ?: mode
    Integer zoneId = getZoneId()
    
    parent?.setZoneHvacMode(zoneId, lennoxMode)
    // Optimistic update so UI shows new mode without waiting for next poll
    sendEvent(name: "thermostatMode", value: mode, descriptionText: "${device.displayName} mode is ${mode}")
    updateThermostatSetpoint()
}

void off() {
    setThermostatMode("off")
}

void heat() {
    setThermostatMode("heat")
}

void cool() {
    setThermostatMode("cool")
}

void auto() {
    setThermostatMode("auto")
}

void emergencyHeat() {
    setThermostatMode("emergency heat")
}

void setThermostatFanMode(String mode) {
    if (logEnable) log.debug "setThermostatFanMode: ${mode}"
    
    if (!(mode in LENNOX_FAN_MODES)) {
        log.error "Invalid fan mode: ${mode}"
        return
    }
    
    Integer zoneId = getZoneId()
    parent?.setZoneFanMode(zoneId, mode)
    // Optimistic update so UI shows new fan mode without waiting for next poll
    sendEvent(name: "thermostatFanMode", value: mode, descriptionText: "${device.displayName} fan mode is ${mode}")
}

void fanAuto() {
    setThermostatFanMode("auto")
}

void fanOn() {
    setThermostatFanMode("on")
}

void fanCirculate() {
    setThermostatFanMode("circulate")
}

void setHeatingSetpoint(BigDecimal setpoint) {
    if (logEnable) log.debug "setHeatingSetpoint: ${setpoint}"
    
    // Validate setpoint
    if (state.minHsp && setpoint < state.minHsp) {
        log.warn "Heating setpoint ${setpoint} below minimum ${state.minHsp}"
        setpoint = state.minHsp
    }
    if (state.maxHsp && setpoint > state.maxHsp) {
        log.warn "Heating setpoint ${setpoint} above maximum ${state.maxHsp}"
        setpoint = state.maxHsp
    }
    
    // Convert to Fahrenheit if needed
    BigDecimal setpointF = getTemperatureScale() == "C" ? celsiusToFahrenheit(setpoint) : setpoint
    
    Integer zoneId = getZoneId()
    
    if (device.currentValue("singleSetpointMode") == "true") {
        parent?.setZoneSetpoints(zoneId, null, null, setpointF)
        // Optimistic update (single setpoint: all three reflect same value)
        sendEvent(name: "thermostatSetpoint", value: setpoint, unit: getTemperatureScale())
        sendEvent(name: "heatingSetpoint", value: setpoint, unit: getTemperatureScale())
        sendEvent(name: "coolingSetpoint", value: setpoint, unit: getTemperatureScale())
    } else {
        parent?.setZoneSetpoints(zoneId, setpointF, null, null)
        sendEvent(name: "heatingSetpoint", value: setpoint, unit: getTemperatureScale(), descriptionText: "${device.displayName} heating setpoint is ${setpoint}°${getTemperatureScale()}")
        updateThermostatSetpoint()
    }
}

void setCoolingSetpoint(BigDecimal setpoint) {
    if (logEnable) log.debug "setCoolingSetpoint: ${setpoint}"
    
    // Validate setpoint
    if (state.minCsp && setpoint < state.minCsp) {
        log.warn "Cooling setpoint ${setpoint} below minimum ${state.minCsp}"
        setpoint = state.minCsp
    }
    if (state.maxCsp && setpoint > state.maxCsp) {
        log.warn "Cooling setpoint ${setpoint} above maximum ${state.maxCsp}"
        setpoint = state.maxCsp
    }
    
    // Convert to Fahrenheit if needed
    BigDecimal setpointF = getTemperatureScale() == "C" ? celsiusToFahrenheit(setpoint) : setpoint
    
    Integer zoneId = getZoneId()
    
    if (device.currentValue("singleSetpointMode") == "true") {
        parent?.setZoneSetpoints(zoneId, null, null, setpointF)
        sendEvent(name: "thermostatSetpoint", value: setpoint, unit: getTemperatureScale())
        sendEvent(name: "heatingSetpoint", value: setpoint, unit: getTemperatureScale())
        sendEvent(name: "coolingSetpoint", value: setpoint, unit: getTemperatureScale())
    } else {
        parent?.setZoneSetpoints(zoneId, null, setpointF, null)
        sendEvent(name: "coolingSetpoint", value: setpoint, unit: getTemperatureScale(), descriptionText: "${device.displayName} cooling setpoint is ${setpoint}°${getTemperatureScale()}")
        updateThermostatSetpoint()
    }
}

void setThermostatSetpoint(BigDecimal setpoint) {
    if (logEnable) log.debug "setThermostatSetpoint: ${setpoint}"
    
    String mode = device.currentValue("thermostatMode")
    
    switch (mode) {
        case "heat":
        case "emergency heat":
            setHeatingSetpoint(setpoint)
            break
        case "cool":
            setCoolingSetpoint(setpoint)
            break
        case "auto":
            // In auto mode, set both with deadband
            BigDecimal deadband = 3  // 3°F deadband
            setHeatingSetpoint(setpoint - (deadband / 2))
            setCoolingSetpoint(setpoint + (deadband / 2))
            break
        default:
            log.warn "Cannot set setpoint in ${mode} mode"
    }
}

// Lennox-specific commands
void setHumidityMode(String mode) {
    if (logEnable) log.debug "setHumidityMode: ${mode}"
    
    if (!(mode in ["off", "humidify", "dehumidify", "both"])) {
        log.error "Invalid humidity mode: ${mode}"
        return
    }
    
    Integer zoneId = getZoneId()
    parent?.setZoneHumidityMode(zoneId, mode)
    sendEvent(name: "humidityMode", value: mode)
}

void setHumidifySetpoint(BigDecimal setpoint) {
    if (logEnable) log.debug "setHumidifySetpoint: ${setpoint}"
    
    Integer sp = setpoint.intValue()
    
    // Validate
    if (state.minHumSp && sp < state.minHumSp) {
        log.warn "Humidify setpoint ${sp} below minimum ${state.minHumSp}"
        sp = state.minHumSp
    }
    if (state.maxHumSp && sp > state.maxHumSp) {
        log.warn "Humidify setpoint ${sp} above maximum ${state.maxHumSp}"
        sp = state.maxHumSp
    }
    
    Integer zoneId = getZoneId()
    parent?.setZoneHumiditySetpoints(zoneId, sp, null)
    sendEvent(name: "humidifySetpoint", value: sp, unit: "%")
}

void setDehumidifySetpoint(BigDecimal setpoint) {
    if (logEnable) log.debug "setDehumidifySetpoint: ${setpoint}"
    
    Integer sp = setpoint.intValue()
    
    // Validate
    if (state.minDehumSp && sp < state.minDehumSp) {
        log.warn "Dehumidify setpoint ${sp} below minimum ${state.minDehumSp}"
        sp = state.minDehumSp
    }
    if (state.maxDehumSp && sp > state.maxDehumSp) {
        log.warn "Dehumidify setpoint ${sp} above maximum ${state.maxDehumSp}"
        sp = state.maxDehumSp
    }
    
    Integer zoneId = getZoneId()
    parent?.setZoneHumiditySetpoints(zoneId, null, sp)
    sendEvent(name: "dehumidifySetpoint", value: sp, unit: "%")
}

void setSchedule(BigDecimal scheduleId) {
    if (logEnable) log.debug "setSchedule: ${scheduleId}"
    
    Integer zoneId = getZoneId()
    parent?.setZoneSchedule(zoneId, scheduleId.intValue())
}

void resumeSchedule() {
    if (logEnable) log.debug "resumeSchedule"
    
    // In Lennox, resume schedule typically means switching to the automatic schedule
    // The default schedule IDs 0-15 are typically the user schedules
    // We'll set to schedule 0 which is usually "Summer"
    Integer zoneId = getZoneId()
    parent?.setZoneSchedule(zoneId, 0)
}

// Helper methods
Integer getZoneId() {
    String zoneIdStr = device.getDataValue("zoneId")
    return zoneIdStr ? zoneIdStr.toInteger() : 0
}

/** Compare two values as numbers so we don't skip updates due to string vs number (e.g. "72" == 72 in Groovy). */
private static Boolean valuesEqualAsNumber(def a, def b) {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    try {
        BigDecimal na = new BigDecimal(a.toString())
        BigDecimal nb = new BigDecimal(b.toString())
        return na.compareTo(nb) == 0
    } catch (Exception e) {
        return a == b
    }
}

def convertTemp(tempF, tempC) {
    if (getTemperatureScale() == "C") {
        return tempC ?: fahrenheitToCelsius(tempF)
    }
    return tempF
}

BigDecimal fahrenheitToCelsius(BigDecimal f) {
    if (f == null) return null
    return ((f - 32) * 5 / 9).setScale(1, BigDecimal.ROUND_HALF_UP)
}

BigDecimal celsiusToFahrenheit(BigDecimal c) {
    if (c == null) return null
    return ((c * 9 / 5) + 32).setScale(0, BigDecimal.ROUND_HALF_UP)
}

void logsOff() {
    log.warn "Debug logging disabled automatically after 24 hours"
    device.updateSetting("logEnable", [type: "bool", value: false])
}
