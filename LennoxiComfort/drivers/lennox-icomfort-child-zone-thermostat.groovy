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
 *  v1.1.3  Schedule preset attribute and setSchedulePreset command (WIP).
 *
 */

import groovy.transform.Field

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
        attribute "schedulePreset", "enum", ["No Schedule", "Schedule IQ", "Save Energy", "Heat Only", "Cool Only", "Schedule"]
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
        command "setSchedulePreset", [[name: "presetName", type: "ENUM", constraints: ["No Schedule", "Schedule IQ", "Save Energy", "Heat Only", "Cool Only", "Schedule"]]]
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

// Only send event when value changed to avoid "Too many events pending" when message pump floods updates
private void sendEventIfChanged(String name, value, String unit = null, String descriptionText = null) {
    def current = device.currentValue(name)
    boolean same = (current == value) || (value != null && current != null && valuesEqualAsNumber(current, value))
    if (same) return
    Map evt = [name: name, value: value]
    if (unit) evt.unit = unit
    if (descriptionText) evt.descriptionText = descriptionText
    sendEvent(evt)
}

// Called by parent to update zone state
void updateZoneState(Map zoneState, Boolean singleSetpointMode) {
    if (logEnable) log.debug "Updating zone state: ${zoneState}"
    
    if (zoneState.temperature != null) {
        def temp = convertTemp(zoneState.temperature, zoneState.temperatureC)
        sendEventIfChanged("temperature", temp, getTemperatureScale(), "${device.displayName} temperature is ${temp}°${getTemperatureScale()}")
    }
    if (zoneState.humidity != null) {
        sendEventIfChanged("humidity", zoneState.humidity, "%", "${device.displayName} humidity is ${zoneState.humidity}%")
    }
    if (zoneState.systemMode) {
        String hubitatMode = HVAC_MODE_MAP[zoneState.systemMode] ?: zoneState.systemMode
        sendEventIfChanged("thermostatMode", hubitatMode, null, "${device.displayName} mode is ${hubitatMode}")
    }
    if (zoneState.fanMode) {
        sendEventIfChanged("thermostatFanMode", zoneState.fanMode, null, "${device.displayName} fan mode is ${zoneState.fanMode}")
    }
    if (zoneState.tempOperation) {
        String opState = OPERATING_STATE_MAP[zoneState.tempOperation] ?: "idle"
        sendEventIfChanged("thermostatOperatingState", opState, null, "${device.displayName} is ${opState}")
    }
    String spmValue = singleSetpointMode.toString()
    sendEventIfChanged("singleSetpointMode", spmValue)
    if (singleSetpointMode) {
        def sp = convertTemp(zoneState.sp, zoneState.spC)
        if (sp != null) {
            String scale = getTemperatureScale()
            sendEventIfChanged("thermostatSetpoint", sp, scale)
            sendEventIfChanged("heatingSetpoint", sp, scale)
            sendEventIfChanged("coolingSetpoint", sp, scale)
        }
    } else {
        def hsp = convertTemp(zoneState.hsp, zoneState.hspC)
        if (hsp != null) {
            sendEventIfChanged("heatingSetpoint", hsp, getTemperatureScale(), "${device.displayName} heating setpoint is ${hsp}°${getTemperatureScale()}")
        }
        def csp = convertTemp(zoneState.csp, zoneState.cspC)
        if (csp != null) {
            sendEventIfChanged("coolingSetpoint", csp, getTemperatureScale(), "${device.displayName} cooling setpoint is ${csp}°${getTemperatureScale()}")
        }
        updateThermostatSetpoint()
    }
    if (zoneState.humidityMode) sendEventIfChanged("humidityMode", zoneState.humidityMode)
    if (zoneState.husp != null) sendEventIfChanged("humidifySetpoint", zoneState.husp, "%")
    if (zoneState.desp != null) sendEventIfChanged("dehumidifySetpoint", zoneState.desp, "%")
    if (zoneState.humOperation) sendEventIfChanged("humidityOperation", zoneState.humOperation)
    if (zoneState.damper != null) sendEventIfChanged("damper", zoneState.damper, "%")
    if (zoneState.demand != null) sendEventIfChanged("demand", zoneState.demand)
    if (zoneState.scheduleId != null) sendEventIfChanged("scheduleId", zoneState.scheduleId)
    // Skip overwriting schedule display if we just set it from preset (cloud may not have returned new state yet)
    Long lastSet = state.lastSchedulePresetSet ?: 0L
    if (now() - lastSet > 25000) {
        state.remove("lastSchedulePresetSet")
        Integer zoneId = getZoneId()
        Integer manualScheduleId = 16 + zoneId
        String displayName
        if (zoneState.scheduleId != null && zoneState.scheduleId == manualScheduleId) {
            displayName = "No Schedule"
        } else if (zoneState.scheduleName) {
            displayName = apiScheduleNameToPreset(zoneState.scheduleName)
        } else {
            displayName = null
        }
        if (displayName) {
            sendEventIfChanged("scheduleName", displayName)
            sendEventIfChanged("schedulePreset", displayName)
        }
    }
    if (zoneState.allergenDefender != null) sendEventIfChanged("allergenDefender", zoneState.allergenDefender.toString())
    if (zoneState.ventilation != null) sendEventIfChanged("ventilation", zoneState.ventilation.toString())
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
    def current = device.currentValue("supportedThermostatModes")
    if (current != null && current.toString() == modes.toString()) return
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
            setpoint = device.currentValue("heatingSetpoint")
            break
    }
    if (setpoint != null) {
        sendEventIfChanged("thermostatSetpoint", setpoint, getTemperatureScale())
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

void setSchedulePreset(String presetName) {
    if (logEnable) log.debug "setSchedulePreset: ${presetName}"
    
    if (!presetName?.trim()) return
    String name = presetName.trim()
    Integer zoneId = getZoneId()
    parent?.setZoneScheduleByPresetName(zoneId, name)
    updateScheduleDisplay(name)
}

/** Called by parent or after setSchedulePreset to update Schedule Name and Schedule Preset so UI reflects the change immediately. */
void updateScheduleDisplay(String presetName) {
    if (!presetName) return
    state.lastSchedulePresetSet = now()
    sendEvent(name: "scheduleName", value: presetName, descriptionText: "${device.displayName} schedule is ${presetName}")
    sendEvent(name: "schedulePreset", value: presetName, descriptionText: "${device.displayName} schedule preset is ${presetName}")
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
/** Map API schedule name (e.g. "manual zone 0", "schedule IQ") to the 6 Lennox-app preset display names. */
private static String apiScheduleNameToPreset(String apiName) {
    if (!apiName) return "No Schedule"
    String lower = apiName.toLowerCase().trim()
    if (lower.startsWith("manual zone")) return "No Schedule"
    if (lower == "schedule iq") return "Schedule IQ"
    if (lower == "save energy") return "Save Energy"
    if (lower == "heat only") return "Heat Only"
    if (lower == "cool only") return "Cool Only"
    if (lower == "schedule") return "Schedule"
    return apiName
}

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
        if (tempC != null) return new BigDecimal(tempC.toString())
        return tempF != null ? fahrenheitToCelsius(new BigDecimal(tempF.toString())) : null
    }
    if (tempF != null) return new BigDecimal(tempF.toString())
    return tempC != null ? celsiusToFahrenheit(new BigDecimal(tempC.toString())) : null
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
