/**
 *  Winix Child Light Child Sensor (Child Driver)
 *
 *  A child device for Winix air purifier ambient light readings.
 *  Exposes as an Illuminance Sensor for HomeKit integration.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

metadata {
    definition(
        name: "Winix Light Sensor",
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/drivers/winix-child-light-sensor-driver.groovy"
    ) {
        capability "IlluminanceMeasurement"
        capability "Sensor"
        capability "Refresh"
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

// ==================== Lifecycle ====================

def installed() {
    logDebug "Installed"
}

def updated() {
    logDebug "Updated"
}

// ==================== Refresh ====================

def refresh() {
    logDebug "Refresh requested"
    
    // Trigger parent refresh
    def parentDevice = getParent()
    if (parentDevice) {
        parentDevice.refresh()
    }
}

// ==================== Update from Parent ====================

def updateIlluminance(Integer value) {
    logDebug "Illuminance updated: ${value} lux"
    sendEvent(name: "illuminance", value: value, unit: "lux")
}

// ==================== Logging ====================

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}
