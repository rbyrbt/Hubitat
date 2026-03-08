/**
 *  Winix Child Air Quality Child Sensor (Child Driver)
 *
 *  A child device for Winix air purifier air quality readings.
 *  Exposes as an Air Quality Sensor for HomeKit integration.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

metadata {
    definition(
        name: "Winix Air Quality Sensor",
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/drivers/winix-child-air-quality-driver.groovy"
    ) {
        capability "AirQuality"
        capability "Sensor"
        capability "Refresh"
        
        // Additional attributes
        attribute "airQualityLevel", "enum", ["good", "fair", "poor"]
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

def updateAirQuality(Integer index, String level) {
    logDebug "Air quality updated: index=${index}, level=${level}"
    sendEvent(name: "airQualityIndex", value: index)
    sendEvent(name: "airQualityLevel", value: level)
}

// ==================== Logging ====================

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}
