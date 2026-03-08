/**
 *  Winix Child Switch (Child Driver)
 *
 *  A generic child switch driver for Winix air purifier features.
 *  Used for Plasmawave, Auto Mode, and Sleep Mode switches.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

metadata {
    definition(
        name: "Winix Child Switch",
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/drivers/winix-child-switch-driver.groovy"
    ) {
        capability "Switch"
        capability "Actuator"
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

// ==================== Switch Capability ====================

def on() {
    logDebug "Turning on"
    
    def switchType = device.getDataValue("switchType")
    def parentDevice = getParent()
    
    if (!parentDevice) {
        log.error "Parent device not found"
        return
    }
    
    switch(switchType) {
        case "plasmawave":
            parentDevice.setPlasmawave("on")
            break
        case "autoMode":
            parentDevice.setAutoMode("on")
            break
        case "sleepMode":
            parentDevice.setSleepMode("on")
            break
        default:
            log.warn "Unknown switch type: ${switchType}"
            return
    }
    
    sendEvent(name: "switch", value: "on")
}

def off() {
    logDebug "Turning off"
    
    def switchType = device.getDataValue("switchType")
    def parentDevice = getParent()
    
    if (!parentDevice) {
        log.error "Parent device not found"
        return
    }
    
    switch(switchType) {
        case "plasmawave":
            parentDevice.setPlasmawave("off")
            break
        case "autoMode":
            parentDevice.setAutoMode("off")
            break
        case "sleepMode":
            parentDevice.setSleepMode("off")
            break
        default:
            log.warn "Unknown switch type: ${switchType}"
            return
    }
    
    sendEvent(name: "switch", value: "off")
}

def refresh() {
    logDebug "Refresh requested"
    
    // Trigger parent refresh
    def parentDevice = getParent()
    if (parentDevice) {
        parentDevice.refresh()
    }
}

// ==================== Update from Parent ====================

def updateState(String newState) {
    logDebug "State updated to: ${newState}"
    sendEvent(name: "switch", value: newState)
}

// ==================== Logging ====================

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}
