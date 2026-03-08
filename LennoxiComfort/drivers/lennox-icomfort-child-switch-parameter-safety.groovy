/**
 *  Lennox iComfort Child Switch - Parameter Safety (Child Driver)
 *
 *  Dedicated child switch for the Parameter Safety lock. Must be turned on before
 *  modifying equipment parameters (e.g. when using diagnostic/equipment settings).
 *  Supports auto-off after a configurable timeout.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

import groovy.transform.Field

@Field static final String VERSION = "1.0.0"
@Field static final String DRIVER_NAME = "Lennox iComfort Child Switch - Parameter Safety"

@Field static final Integer DEFAULT_AUTO_OFF_SECONDS = 60

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-child-switch-parameter-safety.groovy"
    ) {
        capability "Switch"
        capability "Actuator"
        capability "Refresh"

        attribute "switchType", "string"
    }

    preferences {
        input name: "autoOffEnabled", type: "bool", title: "Auto-off enabled", defaultValue: true,
            description: "Automatically turn off after timeout to avoid leaving parameter edits enabled"
        input name: "autoOffSeconds", type: "number", title: "Auto-off time (seconds)", defaultValue: 60,
            description: "Seconds before the switch auto-turns off"
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
    sendEvent(name: "switchType", value: "parameterSafety")
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

void on() {
    if (logEnable) log.debug "Turning on ${device.displayName}"
    turnOnParameterSafety()
}

void off() {
    if (logEnable) log.debug "Turning off ${device.displayName}"
    turnOffParameterSafety()
}

void turnOnParameterSafety() {
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} is on")
    state.parameterSafetyOn = true
    state.parameterSafetyOnTime = now()

    if (settings.autoOffEnabled != false) {
        Integer seconds = settings.autoOffSeconds ?: DEFAULT_AUTO_OFF_SECONDS
        runIn(seconds, "autoOffParameterSafety")
        if (txtEnable) log.info "Parameter safety enabled - will auto-disable in ${seconds} seconds"
    }

    parent?.parameterSafetyStateChanged(true)
}

void turnOffParameterSafety() {
    unschedule("autoOffParameterSafety")
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} is off")
    state.parameterSafetyOn = false
    parent?.parameterSafetyStateChanged(false)
}

void autoOffParameterSafety() {
    if (state.parameterSafetyOn) {
        if (txtEnable) log.info "Parameter safety auto-disabling"
        turnOffParameterSafety()
    }
}

Boolean isParameterSafetyOn() {
    return state.parameterSafetyOn == true
}

void logsOff() {
    log.warn "Debug logging disabled automatically after 24 hours"
    device.updateSetting("logEnable", [type: "bool", value: false])
}
