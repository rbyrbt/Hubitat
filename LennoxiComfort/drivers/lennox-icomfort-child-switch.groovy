/**
 *  Lennox iComfort Child Switch (Child Driver)
 *
 *  Child switch device for Lennox system controls. Supports multiple switch types:
 *  - Ventilation
 *  - Allergen Defender
 *  - Manual Away Mode
 *  - Smart Away Enable
 *  - Zoning (Central Mode)
 *
 *  Parameter Safety uses the dedicated driver: Lennox iComfort Child Switch - Parameter Safety
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

import groovy.transform.Field

@Field static final String VERSION = "1.0.0"
@Field static final String DRIVER_NAME = "Lennox iComfort Child Switch"

// Switch types
@Field static final String SWITCH_TYPE_VENTILATION = "ventilation"
@Field static final String SWITCH_TYPE_ALLERGEN_DEFENDER = "allergenDefender"
@Field static final String SWITCH_TYPE_MANUAL_AWAY = "manualAway"
@Field static final String SWITCH_TYPE_SMART_AWAY_ENABLE = "smartAwayEnable"
@Field static final String SWITCH_TYPE_ZONING = "zoning"

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-child-switch.groovy"
    ) {
        capability "Switch"
        capability "Actuator"
        capability "Refresh"
        
        attribute "switchType", "string"
        attribute "ventilationRemainingTime", "number"
        attribute "ventilatingUntilTime", "string"
        
        // Command for timed ventilation
        command "ventilationTimed", [[name: "minutes", type: "NUMBER", description: "Duration in minutes (0-1440)"]]
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
    String switchType = device.getDataValue("switchType")
    if (switchType) sendEvent(name: "switchType", value: switchType)
}

void refresh() {
    parent?.refresh()
}

// Called by parent to update switch state
void updateSwitchState(Boolean isOn) {
    String currentState = device.currentValue("switch")
    String newState = isOn ? "on" : "off"
    
    if (currentState != newState) {
        if (logEnable) log.debug "Switch state changing from ${currentState} to ${newState}"
        sendEvent(name: "switch", value: newState, descriptionText: "${device.displayName} is ${newState}")
    }
}

// Called by parent to update ventilation-specific attributes
void updateVentilationState(Boolean isOn, Integer remainingTime, String untilTime) {
    updateSwitchState(isOn)
    if (remainingTime != null && device.currentValue("ventilationRemainingTime") != remainingTime) {
        sendEvent(name: "ventilationRemainingTime", value: remainingTime, unit: "seconds")
    }
    if (untilTime != null && device.currentValue("ventilatingUntilTime") != untilTime) {
        sendEvent(name: "ventilatingUntilTime", value: untilTime)
    }
}

void on() {
    if (logEnable) log.debug "Turning on ${device.displayName}"
    
    String switchType = device.getDataValue("switchType")
    
    switch (switchType) {
        case SWITCH_TYPE_VENTILATION:
            parent?.setVentilationMode("on")
            updateSwitchState(true)
            break
        case SWITCH_TYPE_ALLERGEN_DEFENDER:
            parent?.setAllergenDefender("true")
            updateSwitchState(true)
            break
        case SWITCH_TYPE_MANUAL_AWAY:
            parent?.setManualAwayMode("true")
            updateSwitchState(true)
            break
        case SWITCH_TYPE_SMART_AWAY_ENABLE:
            parent?.setSmartAwayEnabled("true")
            updateSwitchState(true)
            break
        case SWITCH_TYPE_ZONING:
            parent?.setCentralMode("false")  // Central mode OFF = Zoning ON
            updateSwitchState(true)
            break
        case "parameterSafety":
            log.warn "Parameter Safety now uses a dedicated driver. Remove this device and add 'Parameter Safety' again from Manage Devices to get auto-off preferences."
            break
        default:
            log.error "Unknown switch type: ${switchType}"
    }
}

void off() {
    if (logEnable) log.debug "Turning off ${device.displayName}"
    
    String switchType = device.getDataValue("switchType")
    
    switch (switchType) {
        case SWITCH_TYPE_VENTILATION:
            parent?.setVentilationMode("off")
            updateSwitchState(false)
            break
        case SWITCH_TYPE_ALLERGEN_DEFENDER:
            parent?.setAllergenDefender("false")
            updateSwitchState(false)
            break
        case SWITCH_TYPE_MANUAL_AWAY:
            parent?.setManualAwayMode("false")
            updateSwitchState(false)
            break
        case SWITCH_TYPE_SMART_AWAY_ENABLE:
            parent?.setSmartAwayEnabled("false")
            updateSwitchState(false)
            break
        case SWITCH_TYPE_ZONING:
            parent?.setCentralMode("true")  // Central mode ON = Zoning OFF
            updateSwitchState(false)
            break
        case "parameterSafety":
            log.warn "Parameter Safety now uses a dedicated driver. Remove this device and add 'Parameter Safety' again from Manage Devices to get auto-off preferences."
            break
        default:
            log.error "Unknown switch type: ${switchType}"
    }
}

// Ventilation-specific timed command
void ventilationTimed(BigDecimal minutes) {
    String switchType = device.getDataValue("switchType")
    
    if (switchType != SWITCH_TYPE_VENTILATION) {
        log.error "ventilationTimed command only available for ventilation switch"
        return
    }
    
    Integer mins = minutes.intValue()
    if (mins < 0 || mins > 1440) {
        log.error "Ventilation time must be between 0 and 1440 minutes"
        return
    }
    
    if (logEnable) log.debug "Setting timed ventilation for ${mins} minutes"
    
    Integer durationSeconds = mins * 60
    parent?.setTimedVentilation(durationSeconds)
}

void logsOff() {
    log.warn "Debug logging disabled automatically after 24 hours"
    device.updateSetting("logEnable", [type: "bool", value: false])
}
