/**
 *  Lennox iComfort Child Sensor (Child Driver)
 *
 *  Multi-purpose sensor child device for Lennox system data. Supports multiple sensor types:
 *  - Outdoor Temperature
 *  - WiFi RSSI / Signal Strength
 *  - Alerts
 *  - Inverter Power
 *  - Home/Away State (Presence)
 *  - Internet Status
 *  - Relay Server Status
 *  - Heat Pump Lockout
 *  - Aux Heat Lockout
 *  - IAQ Sensors (CO2, VOC, PM2.5)
 *  - BLE Sensors
 *  - Weather/Environment
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

import groovy.transform.Field

@Field static final String DRIVER_NAME = "Lennox iComfort Child Sensor"

// Sensor types
@Field static final String SENSOR_TYPE_OUTDOOR_TEMP = "outdoorTemperature"
@Field static final String SENSOR_TYPE_WIFI_RSSI = "wifiRssi"
@Field static final String SENSOR_TYPE_ALERT = "alert"
@Field static final String SENSOR_TYPE_ACTIVE_ALERTS = "activeAlerts"
@Field static final String SENSOR_TYPE_INVERTER_POWER = "inverterPower"
@Field static final String SENSOR_TYPE_HOME_STATE = "homeState"
@Field static final String SENSOR_TYPE_INTERNET_STATUS = "internetStatus"
@Field static final String SENSOR_TYPE_RELAY_STATUS = "relayStatus"
@Field static final String SENSOR_TYPE_CLOUD_STATUS = "cloudStatus"
@Field static final String SENSOR_TYPE_HP_LOCKOUT = "hpLockout"
@Field static final String SENSOR_TYPE_AUX_LOCKOUT = "auxLockout"
@Field static final String SENSOR_TYPE_IAQ_PM25 = "iaqPm25"
@Field static final String SENSOR_TYPE_IAQ_VOC = "iaqVoc"
@Field static final String SENSOR_TYPE_IAQ_CO2 = "iaqCo2"
@Field static final String SENSOR_TYPE_IAQ_OVERALL = "iaqOverall"
@Field static final String SENSOR_TYPE_BLE = "ble"
@Field static final String SENSOR_TYPE_WEATHER_AIR_QUALITY = "weatherAirQuality"
@Field static final String SENSOR_TYPE_WEATHER_POLLEN = "weatherPollen"
@Field static final String SENSOR_TYPE_DIAGNOSTIC = "diagnostic"

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-child-sensor.groovy"
    ) {
        capability "Sensor"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "SignalStrength"
        capability "PowerMeter"
        capability "PresenceSensor"
        capability "Refresh"
        
        attribute "sensorType", "string"
        attribute "sensorValue", "string"
        
        // Alert attributes
        attribute "alertCount", "number"
        attribute "alertText", "string"
        attribute "alertPriority", "string"
        
        // Status attributes (for binary-like sensors)
        attribute "status", "string"
        attribute "statusDetail", "string"
        
        // IAQ attributes
        attribute "iaqScore", "string"
        attribute "iaqValue", "number"
        attribute "iaqSta", "number"
        attribute "iaqLta", "number"
        
        // BLE sensor attributes
        attribute "bleDeviceName", "string"
        attribute "bleDeviceModel", "string"
        attribute "bleCommStatus", "string"
        
        // Weather attributes
        attribute "airQuality", "string"
        attribute "pollenTree", "string"
        attribute "pollenWeed", "string"
        attribute "pollenGrass", "string"
        attribute "moldLevel", "string"
        attribute "uvIndex", "string"
        
        // Diagnostic attributes
        attribute "diagnosticName", "string"
        attribute "diagnosticValue", "string"
        attribute "diagnosticUnit", "string"
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
    String sensorType = device.getDataValue("sensorType")
    if (sensorType) sendEvent(name: "sensorType", value: sensorType)
}

void refresh() {
    parent?.refresh()
}

// Update methods called by parent for different sensor types

void updateTemperature(BigDecimal tempF, BigDecimal tempC) {
    def temp = getTemperatureScale() == "C" ? tempC : tempF
    if (temp != null && device.currentValue("temperature") != temp) {
        sendEvent(name: "temperature", value: temp, unit: getTemperatureScale(), 
                  descriptionText: "${device.displayName} temperature is ${temp}°${getTemperatureScale()}")
        sendEvent(name: "sensorValue", value: "${temp}°${getTemperatureScale()}")
    }
}

void updateHumidity(Integer humidity) {
    if (humidity != null && device.currentValue("humidity") != humidity) {
        sendEvent(name: "humidity", value: humidity, unit: "%",
                  descriptionText: "${device.displayName} humidity is ${humidity}%")
        sendEvent(name: "sensorValue", value: "${humidity}%")
    }
}

void updateSignalStrength(Integer rssi) {
    if (rssi == null) return
    if (device.currentValue("rssi") == rssi) return
    sendEvent(name: "rssi", value: rssi, unit: "dBm",
              descriptionText: "${device.displayName} signal strength is ${rssi} dBm")
    sendEvent(name: "sensorValue", value: "${rssi} dBm")
    Integer lqi = Math.max(0, Math.min(100, (rssi + 100) * 2))
    sendEvent(name: "lqi", value: lqi)
}

void updatePower(BigDecimal watts) {
    if (watts == null) return
    if (device.currentValue("power") == watts) return
    sendEvent(name: "power", value: watts, unit: "W",
              descriptionText: "${device.displayName} power is ${watts}W")
    sendEvent(name: "sensorValue", value: "${watts}W")
}

void updatePresence(Boolean isHome) {
    String presence = isHome ? "present" : "not present"
    if (device.currentValue("presence") != presence) {
        sendEvent(name: "presence", value: presence,
                  descriptionText: "${device.displayName} is ${presence}")
        sendEvent(name: "sensorValue", value: presence)
    }
}

void updateAlerts(Integer count, String alertText, String priority) {
    Integer c = count ?: 0
    String t = alertText ?: "No alerts"
    String p = priority ?: "none"
    if (device.currentValue("alertCount") == c && device.currentValue("alertText") == t && device.currentValue("alertPriority") == p) return
    sendEvent(name: "alertCount", value: c)
    sendEvent(name: "alertText", value: t)
    sendEvent(name: "alertPriority", value: p)
    sendEvent(name: "sensorValue", value: "${c} active alert(s)")
}

void updateStatus(String status, String detail = null) {
    if (device.currentValue("status") == status && device.currentValue("statusDetail") == detail) return
    sendEvent(name: "status", value: status)
    if (detail != null) sendEvent(name: "statusDetail", value: detail)
    sendEvent(name: "sensorValue", value: status)
    String presence = status in ["connected", "online", "true", "enabled", "active"] ? "present" :
                      status in ["disconnected", "offline", "false", "disabled", "inactive"] ? "not present" : null
    if (presence != null && device.currentValue("presence") != presence) {
        sendEvent(name: "presence", value: presence)
    }
}

void updateIaq(String score, BigDecimal value, BigDecimal sta, BigDecimal lta, String unit = null) {
    String s = score ?: "Unknown"
    String displayValue = value != null ? "${value}" : s
    if (unit) displayValue += " ${unit}"
    if (device.currentValue("iaqScore") == s && device.currentValue("iaqValue") == value &&
        device.currentValue("iaqSta") == sta && device.currentValue("iaqLta") == lta &&
        device.currentValue("sensorValue") == displayValue) return
    sendEvent(name: "iaqScore", value: s)
    if (value != null) sendEvent(name: "iaqValue", value: value)
    if (sta != null) sendEvent(name: "iaqSta", value: sta)
    if (lta != null) sendEvent(name: "iaqLta", value: lta)
    sendEvent(name: "sensorValue", value: displayValue)
}

void updateBle(String deviceName, String deviceModel, String commStatus, Map inputs) {
    String name = deviceName ?: "Unknown"
    String model = deviceModel ?: "Unknown"
    String comm = commStatus ?: "Unknown"
    if (device.currentValue("bleDeviceName") != name || device.currentValue("bleDeviceModel") != model ||
        device.currentValue("bleCommStatus") != comm) {
        sendEvent(name: "bleDeviceName", value: name)
        sendEvent(name: "bleDeviceModel", value: model)
        sendEvent(name: "bleCommStatus", value: comm)
    }
    if (inputs) {
        inputs.each { key, inputData ->
            if (inputData.name == "Temperature" && inputData.value != null) {
                updateTemperature(inputData.value, null)
            }
            if (inputData.name == "Humidity" && inputData.value != null) {
                updateHumidity(inputData.value.toInteger())
            }
        }
    }
    String sensorVal = commStatus ?: "Unknown"
    if (device.currentValue("sensorValue") != sensorVal) {
        sendEvent(name: "sensorValue", value: sensorVal)
    }
}

void updateWeather(Map weatherData) {
    if (weatherData.airQuality && device.currentValue("airQuality") != weatherData.airQuality) {
        sendEvent(name: "airQuality", value: weatherData.airQuality)
    }
    if (weatherData.tree != null && device.currentValue("pollenTree") != weatherData.tree) {
        sendEvent(name: "pollenTree", value: weatherData.tree)
    }
    if (weatherData.weed != null && device.currentValue("pollenWeed") != weatherData.weed) {
        sendEvent(name: "pollenWeed", value: weatherData.weed)
    }
    if (weatherData.grass != null && device.currentValue("pollenGrass") != weatherData.grass) {
        sendEvent(name: "pollenGrass", value: weatherData.grass)
    }
    if (weatherData.mold != null && device.currentValue("moldLevel") != weatherData.mold) {
        sendEvent(name: "moldLevel", value: weatherData.mold)
    }
    if (weatherData.uvIndex != null && device.currentValue("uvIndex") != weatherData.uvIndex) {
        sendEvent(name: "uvIndex", value: weatherData.uvIndex)
    }
    if (weatherData.humidity != null) {
        updateHumidity(weatherData.humidity.toInteger())
    }
    String sv = weatherData.airQuality ?: "Unknown"
    if (device.currentValue("sensorValue") != sv) {
        sendEvent(name: "sensorValue", value: sv)
    }
}

void updateDiagnostic(String name, String value, String unit) {
    String n = name ?: "Unknown"
    String v = value ?: "N/A"
    String u = unit ?: ""
    String displayValue = v
    if (u) displayValue += " ${u}"
    if (device.currentValue("diagnosticName") == n && device.currentValue("diagnosticValue") == v &&
        device.currentValue("diagnosticUnit") == u) return
    sendEvent(name: "diagnosticName", value: n)
    sendEvent(name: "diagnosticValue", value: v)
    sendEvent(name: "diagnosticUnit", value: u)
    sendEvent(name: "sensorValue", value: displayValue)
}

// Generic update method that routes to appropriate handler based on sensor type
void updateSensorValue(Map data) {
    String sensorType = device.getDataValue("sensorType")
    
    switch (sensorType) {
        case SENSOR_TYPE_OUTDOOR_TEMP:
            updateTemperature(data.tempF, data.tempC)
            break
        case SENSOR_TYPE_WIFI_RSSI:
            updateSignalStrength(data.rssi)
            break
        case SENSOR_TYPE_ALERT:
        case SENSOR_TYPE_ACTIVE_ALERTS:
            updateAlerts(data.count, data.text, data.priority)
            break
        case SENSOR_TYPE_INVERTER_POWER:
            updatePower(data.power)
            break
        case SENSOR_TYPE_HOME_STATE:
            updatePresence(data.isHome)
            break
        case SENSOR_TYPE_INTERNET_STATUS:
        case SENSOR_TYPE_RELAY_STATUS:
        case SENSOR_TYPE_CLOUD_STATUS:
        case SENSOR_TYPE_HP_LOCKOUT:
        case SENSOR_TYPE_AUX_LOCKOUT:
            updateStatus(data.status, data.detail)
            break
        case SENSOR_TYPE_IAQ_PM25:
        case SENSOR_TYPE_IAQ_VOC:
        case SENSOR_TYPE_IAQ_CO2:
        case SENSOR_TYPE_IAQ_OVERALL:
            updateIaq(data.score, data.value, data.sta, data.lta, data.unit)
            break
        case SENSOR_TYPE_BLE:
            updateBle(data.deviceName, data.deviceModel, data.commStatus, data.inputs)
            break
        case SENSOR_TYPE_WEATHER_AIR_QUALITY:
        case SENSOR_TYPE_WEATHER_POLLEN:
            updateWeather(data)
            break
        case SENSOR_TYPE_DIAGNOSTIC:
            updateDiagnostic(data.name, data.value, data.unit)
            break
        default:
            log.warn "Unknown sensor type: ${sensorType}"
            sendEvent(name: "sensorValue", value: data.toString())
    }
}

void logsOff() {
    log.warn "Debug logging disabled automatically after 24 hours"
    device.updateSetting("logEnable", [type: "bool", value: false])
}
