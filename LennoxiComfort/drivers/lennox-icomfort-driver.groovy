/**
 *  Lennox iComfort Driver (Parent Driver)
 *
 *  Communicates with Lennox iComfort S30/E30/S40/M30 thermostats via local LAN or cloud connection.
 *  Creates child devices for zones, switches, and sensors.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

@Field static final String DRIVER_NAME = "Lennox iComfort Driver"

// Connection states
@Field static final String STATE_DISCONNECTED = "Disconnected"
@Field static final String STATE_CONNECTING = "Connecting"
@Field static final String STATE_CONNECTED = "Connected"
@Field static final String STATE_LOGIN_FAILED = "Login Failed"
@Field static final String STATE_RETRY_WAIT = "Waiting to Retry"

// Ventilation modes
@Field static final String LENNOX_VENTILATION_MODE_ON = "on"
@Field static final String LENNOX_VENTILATION_MODE_OFF = "off"
@Field static final String LENNOX_VENTILATION_MODE_INSTALLER = "installer"

// Default app IDs
@Field static final String DEFAULT_LOCAL_APP_ID = "hubitat"
@Field static final String DEFAULT_CLOUD_APP_ID = "mapp079372367644467046827099"

// Poll interval (seconds) during initial discovery; after discovery complete, user-configured interval is used
@Field static final Integer DISCOVERY_POLL_INTERVAL_LOCAL_SEC = 2
@Field static final Integer DISCOVERY_POLL_INTERVAL_CLOUD_SEC = 10

// Cloud API URLs
@Field static final String CLOUD_AUTHENTICATE_URL = "https://ic3messaging.myicomfort.com/v1/mobile/authenticate"
@Field static final String CLOUD_LOGIN_URL = "https://ic3messaging.myicomfort.com/v2/user/login"
@Field static final String CLOUD_NEGOTIATE_URL = "https://icnotificationservice.myicomfort.com/LennoxNotificationServer/negotiate"
@Field static final String CLOUD_RETRIEVE_URL = "https://icretrieveapi.myicomfort.com/v1/messages/retrieve"
@Field static final String CLOUD_REQUESTDATA_URL = "https://icrequestdataapi.myicomfort.com/v1/Messages/RequestData"
@Field static final String CLOUD_PUBLISH_URL = "https://icpublishapi.myicomfort.com/v1/messages/publish"
@Field static final String CLOUD_LOGOUT_URL = "https://ic3messaging.myicomfort.com/v1/user/logout"

// User agent for cloud connections
@Field static final String USER_AGENT = "lx_ic3_mobile_appstore/3.75.218 (iPad; iOS 14.4.1; Scale/2.00)"

// Lennox cloud certificate for authentication
@Field static final String CLOUD_CERTIFICATE = "MIIKXAIBAzCCChgGCSqGSIb3DQEHAaCCCgkEggoFMIIKATCCBfoGCSqGSIb3DQEHAaCCBesEggXnMIIF4zCCBd8GCyqGSIb3DQEMCgECoIIE/jCCBPowHAYKKoZIhvcNAQwBAzAOBAhvt2dVYDpuhgICB9AEggTYM43UVALue2O5a2GqZ6xPFv1ZOGby+M3I/TOYyVwHDBR+UAYNontMWLvUf6xE/3+GUj/3lBcXk/0erw7iQXa/t9q9b8Xk2r7FFuf+XcWvbXcvcPG0uP74Zx7Fj8HcMmD0/8NNcH23JnoHiWLaa1walfjZG6fZtrOjx4OmV6oYMdkRZm9tP5FuJenPIwdDFx5dEKiWdjdJW0lRl7jWpvbU63gragLBHFqtkCSRCQVlUALtO9Uc2W+MYwh658HrbWGsauLKXABuHjWCK8fiLm1Tc6cuNP/hUF+j3kxt2tkXIYlMhWxEAOUicC0m8wBtJJVCDQQLzwN5PebGGXiq04F40IUOccl9RhaZ2PdWLChaqq+CNQdUZ1mDYcdfg5SVMmiMJayRAA7MWY/t4W53yTU0WXCPu3mg0WPhuRUuphaKdyBgOlmBNrXq/uXjcXgTPqKAKHsph3o6K2TWcPdRBswwc6YJ88J21bLD83fT+LkEmCSldPz+nvLIuQIDZcFnTdUJ8MZRh+QMQgRibyjQwBg02XoEVFg9TJenXVtYHN0Jpvr5Bvd8FDMHGW/4kPM4mODo0PfvHj9wgqMMgTqiih8LfmuJQm30BtqRNm3wHCW1wZ0bbVqefvRSUy82LOxQ9443zjzSrBf7/cFk+03iNn6t3s65ubzuW7syo4lnXwm3DYVR32wo/WmpZVJ3NLeWgypGjNA7MaSwZqUas5lY1EbxLXM5WLSXVUyCqGCdKYFUUKDMahZ6xqqlHUuFj6T49HNWXE7lAdSAOq7yoThMYUVvjkibKkji1p1TIAtXPDPVgSMSsWG1aJilrpZsRuipFRLDmOmbeanS+TvX5ctTa1px/wSeHuAYD/t+yeIlZriajAk62p2ZGENRPIBCbLxx1kViXJBOSgEQc8ItnBisti5N9gjOYoZT3hoONd/IalOxcVU9eBTuvMoVCPMTxYvSz6EUaJRoINS6yWfzriEummAuH6mqENWatudlqKzNAH4RujRetKdvToTddIAGYDJdptzzPIu8OlsmZWTv9HxxUEGYXdyqVYDJkY8dfwB1fsa9vlV3H7IBMjx+nG4ESMwi7UYdhFNoBa7bLD4P1yMQdXPGUs1atFHmPrXYGf2kIdvtHiZ149E9ltxHjRsEaXdhcoyiDVdraxM2H46Y8EZNhdCFUTr2vMau3K/GcU5QMyzY0Z1qD7lajQaBIMGJRZQ6xBnQAxkd4xU1RxXOIRkPPiajExENuE9v9sDujKAddJxvNgBp0e8jljt7ztSZ+QoMbleJx7m9s3sqGvPK0eREzsn/2aQBA+W3FVe953f0Bk09nC6CKi7QwM4uTY9x2IWh/nsKPFSD0ElXlJzJ3jWtLpkpwNL4a8CaBAFPBB2QhRf5bi52KxaAD0TXvQPHsaTPhmUN827smTLoW3lbOmshk4ve1dPAyKPl4/tHvto/EGlYnQf0zjs6BATu/4pJFJz+n0duyF1y/F/elBDXPclJvfyZhEFT99txYsSm2GUijXKOHW/sjMalQctiAyg8Y5CzrOJUhKkB/FhaN5wjJLFz7ZCEJBV7Plm3aNPegariTkLCgkFZrFvrIppvRKjR41suXKP/WhdWhu0Ltb+QgC+8OQTC8INq3v1fdDxT2HKNShVTSubmrUniBuF5MDGBzTATBgkqhkiG9w0BCRUxBgQEAQAAADBXBgkqhkiG9w0BCRQxSh5IADAANgAyAGQANQA5ADMANQAtADYAMAA5AGUALQA0ADYAMgA2AC0AOQA2ADUAZAAtADcAMwBlAGQAMQAwAGUAYwAzAGYAYgA4MF0GCSsGAQQBgjcRATFQHk4ATQBpAGMAcgBvAHMAbwBmAHQAIABTAHQAcgBvAG4AZwAgAEMAcgB5AHAAdABvAGcAcgBhAHAAaABpAGMAIABQAHIAbwB2AGkAZABlAHIwggP/BgkqhkiG9w0BBwagggPwMIID7AIBADCCA+UGCSqGSIb3DQEHATAcBgoqhkiG9w0BDAEGMA4ECFK0DO//E1DsAgIH0ICCA7genbD4j1Y4WYXkuFXxnvvlNmFsw3qPiHn99RVfc+QFjaMvTEqk7BlEBMduOopxUAozoDAv0o+no/LNIgKRXdHZW3i0GPbmoj2WjZJW5T6Z0QVlS5YlQgvbSKVee51grg6nyjXymWgEmrzVldDxy/MfhsxNQUfaLm3awnziFb0l6/m9SHj2eZfdB4HOr2r9BXA6oSQ+8tbGHT3dPnCVAUMjht1MNo6u7wTRXIUYMVn+Aj/xyF9uzDRe404yyenNDPqWrVLoP+Nzssocoi+U+WUFCKMBdVXbM/3GYAuxXV+EHAgvVWcP4deC9ukNPJIdA8gtfTH0Bjezwrw+s+nUy72ROBzfQl9t/FHzVfIZput5GcgeiVppQzaXZMBu/LIIQ9u/1Q7xMHd+WsmNsMlV6eekdO4wcCIo/mM+k6Yukf2o8OGjf1TRwbpt3OH8ID5YRIy848GT49JYRbhNiUetYf5s8cPglk/Q4E2oyNN0LuhTAJtXOH2Gt7LsDVxCDwCA+mUJz1SPAVMVY8hz/h8l4B6sXkwOz3YNe/ILAFncS2o+vD3bxZrYec6TqN+fdkLf1PeKH62YjbFweGR1HLq7R1nD76jinE3+lRZZrfOFWaPMBcGroWOVS0ix0h5r8+lM6n+/hfOS8YTF5Uy++AngQR18IJqT7+SmnLuENgyG/9V53Z7q7BwDo7JArx7tosmxmztcubNCbLFFfzx7KBCIjU1PjFTAtdNYDho0CG8QDfvSQHz9SzLYnQXXWLKRseEGQCW59JnJVXW911FRt4Mnrh5PmLMoaxbf43tBR2xdmaCIcZgAVSjV3sOCfJgja6mKFsb7puzYRBLqYkfQQdOlrnHHrLSkjaqyQFBbpfROkRYo9sRejPMFMbw/Orreo+7YELa+ZoOpS/yZAONgQZ6tlZ4VR9TI5LeLH5JnnkpzpRvHoNkWUtKA+YHqY5Fva3e3iV82O4BwwmJdFXP2RiRQDJYVDzUe5KuurMgduHjqnh8r8238pi5iRZOKlrR7YSBdRXEU9R5dx+i4kv0xqoXKcQdMflE+X4YMd7+BpCFS3ilgbb6q1DuVIN5Bnayyeeuij7sR7jk0z6hV8lt8FZ/Eb+Sp0VB4NeXgLbvlWVuq6k+0ghZkaC1YMzXrfM7N+jy2k1L4FqpO/PdvPRXiA7uiH7JsagI0Uf1xbjA3wbCj3nEi3H/xoyWXgWh2P57m1rxjW1earoyc1CWkRgZLnNc1lNTWVA6ghCSMbCh7T79Fr5GEY2zNcOiqLHS3MDswHzAHBgUrDgMCGgQU0GYHy2BCdSQK01QDvBRI797NPvkEFBwzcxzJdqixLTllqxfI9EJ3KSBwAgIH0A=="

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "rbyrbt",
        author: "rbyrbt",
        importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-driver.groovy"
    ) {
        capability "Initialize"
        capability "Refresh"
        capability "Configuration"
        
        attribute "connectionState", "string"
        attribute "systemName", "string"
        attribute "outdoorTemperature", "number"
        attribute "outdoorTemperatureStatus", "string"
        attribute "numberOfZones", "number"
        attribute "serialNumber", "string"
        attribute "softwareVersion", "string"
        attribute "productType", "string"
        attribute "zoningMode", "string"
        attribute "allergenDefender", "string"
        attribute "ventilationMode", "string"
        attribute "manualAwayMode", "string"
        attribute "smartAwayEnabled", "string"
        attribute "internetStatus", "string"
        attribute "relayServerConnected", "string"
        attribute "wifiRssi", "number"
        attribute "lastMessageTime", "string"
        attribute "messageCount", "number"
        
        command "connect"
        command "disconnect"
        command "setAllergenDefender", [[name: "enabled", type: "ENUM", constraints: ["true", "false"]]]
        command "setVentilationMode", [[name: "mode", type: "ENUM", constraints: ["on", "off", "installer"]]]
        command "setManualAwayMode", [[name: "enabled", type: "ENUM", constraints: ["true", "false"]]]
        command "setSmartAwayEnabled", [[name: "enabled", type: "ENUM", constraints: ["true", "false"]]]
        command "cancelSmartAway"
        command "setCirculateTime", [[name: "percent", type: "NUMBER", description: "15-45 percent"]]
        command "setDiagnosticLevel", [[name: "level", type: "ENUM", constraints: ["0", "1", "2"]]]
        command "setDehumidificationMode", [[name: "mode", type: "ENUM", constraints: ["auto", "medium", "high"]]]
        command "setCentralMode", [[name: "enabled", type: "ENUM", constraints: ["true", "false"]]]
        command "setTimedVentilation", [[name: "seconds", type: "NUMBER", description: "Duration in seconds"]]
        command "clearAlert", [[name: "alertId", type: "NUMBER", description: "Alert ID to clear"]]
        command "setEquipmentParameter", [[name: "equipId", type: "NUMBER"], [name: "paramId", type: "NUMBER"], [name: "value", type: "STRING"]]
        command "getEquipmentDiagnostics"
        command "refreshToken"
        command "removeChildDevices"
    }
    
    preferences {
        input name: "connectionType", type: "enum", title: "Connection Type", 
              options: ["cloud": "Lennox Cloud", "local": "Local LAN"], required: true, submitOnChange: true
        input name: "email", type: "string", title: "Email (Cloud)", description: "Lennox iComfort account email", required: false
        input name: "password", type: "password", title: "Password (Cloud)", description: "Lennox iComfort account password", required: false
        input name: "ipAddress", type: "string", title: "IP Address (Local)", description: "IP address of your Lennox thermostat", required: false
        input name: "appId", type: "string", title: "Application ID", description: "Leave blank for default", required: false
        if (settings?.connectionType == "local") {
            input name: "pollInterval", type: "enum", title: "Poll Interval (Local)",
                  options: localPollOptions(), defaultValue: "30", required: true,
                  description: "Seconds between Retrieve requests. 30s recommended. Very short intervals (e.g. 10s) can overwhelm the S30 and cause delayed or stale updates for 2–3 minutes; use 30s or higher if you see that."
        } else {
            input name: "pollInterval", type: "enum", title: "Poll Interval (Cloud)",
                  options: cloudPollOptions(), defaultValue: "60", required: true
        }
        input name: "fastPollInterval", type: "decimal", title: "Fast Poll Interval (seconds)",
              description: "Polling rate immediately after sending a command", defaultValue: 0.75, required: true
        input name: "fastPollCount", type: "number", title: "Fast Poll Count",
              description: "Number of fast polls after a command", defaultValue: 10, required: true
        if (settings?.connectionType == "local") {
            input name: "longPollTimeout", type: "number", title: "Long Poll Timeout (seconds)",
                  description: "How long each Retrieve request blocks on the thermostat (local only). Lower (e.g. 5) gives the S30 more time between connections and can improve responsiveness; default 15.", defaultValue: 15, required: true
        }
        input name: "createSensors", type: "bool", title: "Create Sensor Devices", defaultValue: true
        input name: "createSwitches", type: "bool", title: "Create Switch Devices", defaultValue: true
        input name: "createDiagnosticSensors", type: "bool", title: "Create Diagnostic Sensor Devices",
              description: "Creates child sensors for equipment diagnostics when diagnostic level is > 0 (can be 40-50 sensors per equipment)", defaultValue: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

// Lifecycle methods
void installed() {
    log.info "${DRIVER_NAME} installed"
    initialize()
}

void updated() {
    log.info "${DRIVER_NAME} updated"
    unschedule()
    if (logEnable) runIn(86400, "logsOff")
    initialize()
}

void initialize() {
    log.info "${DRIVER_NAME} initializing..."
    
    state.publishMessageId = 1
    state.messageCount = 0
    state.fastPollRemaining = 0
    if (!state.zones) state.zones = [:]
    if (!state.schedules) state.schedules = [:]
    if (!state.equipment) state.equipment = [:]
    state.systemInitialized = false
    state.zonesDiscovered = false
    
    sendEvent(name: "connectionState", value: STATE_DISCONNECTED)
    
    if (settings.connectionType == "cloud" && settings.email && settings.password) {
        connect()
    } else if (settings.connectionType == "local" && settings.ipAddress) {
        connect()
    } else {
        log.warn "Please configure connection settings"
    }
}

void configure() {
    log.info "Configuring ${DRIVER_NAME}"
    initialize()
}

void configureFromApp(Map config) {
    log.info "Configuring ${DRIVER_NAME} from app..."

    if (config.connectionType) device.updateSetting("connectionType", [type: "enum", value: config.connectionType])
    if (config.ipAddress) device.updateSetting("ipAddress", [type: "string", value: config.ipAddress])
    if (config.email) device.updateSetting("email", [type: "string", value: config.email])
    if (config.password) device.updateSetting("password", [type: "password", value: config.password])
    if (config.appId) device.updateSetting("appId", [type: "string", value: config.appId])
    if (config.pollInterval) device.updateSetting("pollInterval", [type: "enum", value: config.pollInterval.toString()])
    if (config.fastPollInterval != null) device.updateSetting("fastPollInterval", [type: "decimal", value: config.fastPollInterval])
    if (config.fastPollCount != null) device.updateSetting("fastPollCount", [type: "number", value: config.fastPollCount])
    if (config.longPollTimeout != null) device.updateSetting("longPollTimeout", [type: "number", value: config.longPollTimeout])
    if (config.createSensors != null) device.updateSetting("createSensors", [type: "bool", value: config.createSensors])
    if (config.createSwitches != null) device.updateSetting("createSwitches", [type: "bool", value: config.createSwitches])
    if (config.createDiagnosticSensors != null) device.updateSetting("createDiagnosticSensors", [type: "bool", value: config.createDiagnosticSensors])
    if (config.logEnable != null) device.updateSetting("logEnable", [type: "bool", value: config.logEnable])
    if (config.txtEnable != null) device.updateSetting("txtEnable", [type: "bool", value: config.txtEnable])

    runIn(2, "initialize")
}

void updateGlobalSettings(Map config) {
    if (logEnable) log.debug "Updating global settings from app..."
    Boolean pollingChanged = false

    if (config.logEnable != null) {
        device.updateSetting("logEnable", [type: "bool", value: config.logEnable])
        if (config.logEnable) {
            runIn(86400, "logsOff")
        } else {
            unschedule("logsOff")
        }
    }
    if (config.txtEnable != null) {
        device.updateSetting("txtEnable", [type: "bool", value: config.txtEnable])
    }
    if (config.pollInterval != null) {
        device.updateSetting("pollInterval", [type: "enum", value: config.pollInterval.toString()])
        pollingChanged = true
    }
    if (config.fastPollInterval != null) {
        device.updateSetting("fastPollInterval", [type: "decimal", value: config.fastPollInterval])
    }
    if (config.fastPollCount != null) {
        device.updateSetting("fastPollCount", [type: "number", value: config.fastPollCount])
    }
    if (config.longPollTimeout != null) {
        device.updateSetting("longPollTimeout", [type: "number", value: config.longPollTimeout])
    }

    getChildDevices().each { child ->
        try {
            if (config.logEnable != null) child.updateSetting("logEnable", [type: "bool", value: config.logEnable])
            if (config.txtEnable != null) child.updateSetting("txtEnable", [type: "bool", value: config.txtEnable])
        } catch (Exception e) {
            log.warn "Could not update settings on child ${child.deviceNetworkId}: ${e.message}"
        }
    }

    if (pollingChanged && state.connected) {
        unschedule("messagePump")
        scheduleNextPoll()
    }
}

Map getSystemHealth() {
    return [
        connectionState: device.currentValue("connectionState") ?: "Unknown",
        connected: state.connected ?: false,
        systemName: state.systemName ?: "Unknown",
        serialNumber: state.serialNumber ?: "N/A",
        softwareVersion: state.softwareVersion ?: "N/A",
        productType: state.productType ?: "N/A",
        outdoorTemperature: state.outdoorTemperature,
        numberOfZones: state.numberOfZones ?: 0,
        wifiRssi: state.wifiRssi,
        messageCount: state.messageCount ?: 0,
        isLocalConnection: state.isLocalConnection ?: false,
        sysId: state.sysId
    ]
}

void refresh() {
    log.info "Refreshing ${DRIVER_NAME}"
    if (state.connected) {
        subscribe()
    }
}

// Connection management
void connect() {
    log.info "Connecting to Lennox system..."
    sendEvent(name: "connectionState", value: STATE_CONNECTING)
    
    state.connected = false
    
    if (settings.connectionType == "local") {
        connectLocal()
    } else {
        connectCloud()
    }
}

void disconnect() {
    log.info "Disconnecting from Lennox system..."
    unschedule("messagePump")
    unschedule("connect")
    
    if (state.connected) {
        if (settings.connectionType == "local") {
            disconnectLocal()
        } else {
            disconnectCloud()
        }
    }
    
    state.connected = false
    sendEvent(name: "connectionState", value: STATE_DISCONNECTED)
}

// Local connection methods
void connectLocal() {
    String appId = sanitizeAppId(settings.appId) ?: DEFAULT_LOCAL_APP_ID
    String url = "https://${settings.ipAddress}/Endpoints/${appId}/Connect"
    
    if (logEnable) log.debug "Local connect URL: ${url}"
    
    // ignoreSSLIssues: thermostat may use self-signed cert; only use on trusted LAN
    // No body and no Content-Type to avoid 400 from Lennox header quirks
    Map params = [
        uri: url,
        ignoreSSLIssues: true,
        timeout: 30
    ]
    
    try {
        httpPost(params) { resp ->
            if (resp.status == 200 || resp.status == 204) {
                log.info "Local connection established"
                state.connected = true
                state.isLocalConnection = true
                state.appId = appId
                resetReconnectBackoff()
                sendEvent(name: "connectionState", value: STATE_CONNECTED)
                
                // Subscribe to data
                runIn(1, "subscribe")
            } else {
                log.error "Local connect failed with status: ${resp.status}"
                sendEvent(name: "connectionState", value: STATE_LOGIN_FAILED)
                scheduleReconnect()
            }
        }
    } catch (Exception e) {
        log.error "Local connect exception: ${e.message}"
        sendEvent(name: "connectionState", value: STATE_DISCONNECTED)
        scheduleReconnect()
    }
}

void disconnectLocal() {
    String appId = state.appId ?: DEFAULT_LOCAL_APP_ID
    String url = "https://${settings.ipAddress}/Endpoints/${appId}/Disconnect"
    
    Map params = [
        uri: url,
        ignoreSSLIssues: true,
        timeout: 10
    ]
    
    try {
        httpPost(params) { resp ->
            if (logEnable) log.debug "Local disconnect response: ${resp.status}"
        }
    } catch (Exception e) {
        log.warn "Local disconnect exception: ${e.message}"
    }
}

// Cloud connection methods
void connectCloud() {
    log.info "Connecting to Lennox cloud..."
    
    // Step 1: Authenticate with certificate
    authenticateCloud()
}

void authenticateCloud(Integer attempt = 0) {
    Integer maxRetries = 5
    log.info "Authenticating with Lennox cloud${attempt > 0 ? " (retry ${attempt} of ${maxRetries - 1})" : ""}..."

    Map params = [
        uri: CLOUD_AUTHENTICATE_URL,
        contentType: "text/plain",
        body: CLOUD_CERTIFICATE,
        timeout: 30
    ]

    state.authAttempt = attempt
    asynchttpPost("handleAuthenticateResponse", params)
}

void handleAuthenticateResponse(resp, data) {
    Integer attempt = state.authAttempt ?: 0
    Integer maxRetries = 5

    try {
        if (resp.status == 200) {
            def json = new JsonSlurper().parseText(resp.getData())
            state.authBearerToken = json?.serverAssigned?.security?.certificateToken?.encoded

            if (state.authBearerToken) {
                log.info "Cloud authentication successful"
                runIn(1, "loginCloud")
            } else {
                log.error "Authentication response missing token"
                sendEvent(name: "connectionState", value: STATE_LOGIN_FAILED)
                scheduleReconnect()
            }
        } else {
            String errBody = ""
            try { errBody = resp.getData() ?: "" } catch (Exception ignored) {}
            log.warn "Cloud authentication rejected (attempt ${attempt + 1}): HTTP ${resp.status} - ${errBody}"
            if (attempt + 1 < maxRetries) {
                state.authAttempt = attempt + 1
                runIn(2, "authenticateCloudRetry")
            } else {
                log.error "Cloud authentication failed after ${maxRetries} attempts: HTTP ${resp.status} - ${errBody}"
                sendEvent(name: "connectionState", value: STATE_DISCONNECTED)
                scheduleReconnect()
            }
        }
    } catch (Exception e) {
        log.warn "Cloud authentication exception (attempt ${attempt + 1}): ${e.message}"
        if (attempt + 1 < maxRetries) {
            state.authAttempt = attempt + 1
            runIn(2, "authenticateCloudRetry")
        } else {
            log.error "Cloud authentication failed after ${maxRetries} attempts: ${e.message}"
            sendEvent(name: "connectionState", value: STATE_DISCONNECTED)
            scheduleReconnect()
        }
    }
}

void authenticateCloudRetry() {
    authenticateCloud(state.authAttempt ?: 0)
}

void loginCloud() {
    log.info "Logging into Lennox cloud..."
    
    String appId = sanitizeAppId(settings.appId) ?: DEFAULT_CLOUD_APP_ID
    
    String body = "username=${URLEncoder.encode(settings.email, 'UTF-8')}&password=${URLEncoder.encode(settings.password, 'UTF-8')}&grant_type=password&applicationid=${appId}"
    
    Map params = [
        uri: CLOUD_LOGIN_URL,
        requestContentType: "text/plain",
        contentType: "application/json",
        body: body,
        timeout: 30,
        headers: [
            "Authorization": state.authBearerToken,
            "User-Agent": USER_AGENT,
            "Accept": "*/*"
        ]
    ]
    
    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                def json = resp.data instanceof String ? new JsonSlurper().parseText(resp.data) : resp.data
                
                // Extract bearer token
                state.loginBearerToken = json?.ServerAssignedRoot?.serverAssigned?.security?.userToken?.encoded
                
                // Extract the token part (without "Bearer ")
                if (state.loginBearerToken?.startsWith("Bearer ")) {
                    state.loginToken = state.loginBearerToken.substring(7)
                } else if (state.loginBearerToken?.startsWith("bearer ")) {
                    state.loginToken = state.loginBearerToken.substring(7)
                } else {
                    state.loginToken = state.loginBearerToken
                }
                
                // Process homes and systems
                if (json.readyHomes?.homes) {
                    state.homes = []
                    state.systems = []
                    
                    json.readyHomes.homes.each { home ->
                        Map homeInfo = [
                            id: home.id,
                            homeId: home.homeId,
                            name: home.name
                        ]
                        state.homes.add(homeInfo)
                        
                        home.systems?.each { system ->
                            Map sysInfo = [
                                id: system.id,
                                sysId: system.sysId,
                                homeId: home.homeId
                            ]
                            state.systems.add(sysInfo)
                            
                            // Use the first system's sysId
                            if (!state.sysId) {
                                state.sysId = system.sysId
                            }
                        }
                    }
                    
                    log.info "Cloud login successful - found ${state.homes.size()} home(s) and ${state.systems.size()} system(s)"
                }
                
                state.appId = appId
                state.isLocalConnection = false
                
                // Step 3: Negotiate connection
                runIn(1, "negotiateCloud")
                
            } else {
                log.error "Cloud login failed with status: ${resp.status}"
                sendEvent(name: "connectionState", value: STATE_LOGIN_FAILED)
                scheduleReconnect()
            }
        }
    } catch (Exception e) {
        log.error "Cloud login exception: ${e.message}"
        sendEvent(name: "connectionState", value: STATE_LOGIN_FAILED)
        scheduleReconnect()
    }
}

void negotiateCloud() {
    log.info "Negotiating cloud connection..."
    
    String clientId = getClientId()
    String url = "${CLOUD_NEGOTIATE_URL}?clientProtocol=1.3.0.0&clientId=${URLEncoder.encode(clientId, 'UTF-8')}&Authorization=${URLEncoder.encode(state.loginToken, 'UTF-8')}"
    
    Map params = [
        uri: url,
        contentType: "application/json",
        timeout: 30,
        headers: [
            "User-Agent": USER_AGENT,
            "Accept": "*/*"
        ]
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                def json = resp.data instanceof String ? new JsonSlurper().parseText(resp.data) : resp.data
                
                state.connectionId = json.ConnectionId
                state.connectionToken = json.ConnectionToken
                state.tryWebsockets = json.TryWebSockets
                state.streamUrl = json.Url
                
                log.info "Cloud negotiation successful"
                
                state.connected = true
                resetReconnectBackoff()
                sendEvent(name: "connectionState", value: STATE_CONNECTED)
                
                // Subscribe to data for each system
                runIn(1, "subscribeCloud")
                
            } else {
                log.error "Cloud negotiation failed with status: ${resp.status}"
                sendEvent(name: "connectionState", value: STATE_LOGIN_FAILED)
                scheduleReconnect()
            }
        }
    } catch (Exception e) {
        log.error "Cloud negotiation exception: ${e.message}"
        sendEvent(name: "connectionState", value: STATE_LOGIN_FAILED)
        scheduleReconnect()
    }
}

void subscribeCloud(Set<String> pathGroups) {
    log.info "Subscribing to cloud data..."
    
    String cloudPaths1, cloudPaths2
    if (pathGroups == null) {
        if (logEnable) log.debug "RequestData mode: discovery (full paths for initial setup)"
        cloudPaths1 = "1;/zones;/occupancy;/schedules;/reminderSensors;/reminders;/alerts/active;"
        cloudPaths2 = "1;/alerts/meta;/dealers;/devices;/equipments;/system;/fwm;/ocst;"
    } else {
        if (logEnable) log.debug "RequestData mode: dynamic | path groups: ${pathGroups.sort().join(', ')}"
        cloudPaths1 = buildCloudPaths1(pathGroups)
        cloudPaths2 = buildCloudPaths2(pathGroups)
        if (logEnable) log.debug "RequestData cloud paths1: ${cloudPaths1}"
        if (logEnable) log.debug "RequestData cloud paths2: ${cloudPaths2}"
    }
    state.cloudPaths2 = cloudPaths2
    
    state.systems?.each { system ->
        String sysId = system.sysId
        cloudRequestData(sysId, cloudPaths1)
        runIn(2, "cloudRequestDataSecondary", [data: sysId])
    }
    
    runIn(4, "startMessagePump")
}

void cloudRequestDataSecondary(String sysId) {
    String paths2 = state.cloudPaths2 ?: "1;/alerts/meta;/dealers;/devices;/equipments;/system;/fwm;/ocst;"
    cloudRequestData(sysId, paths2)
}

void cloudRequestData(String sysId, String jsonPaths) {
    if (!state.loginBearerToken) {
        log.error "No bearer token available for cloud request"
        return
    }
    
    Map message = [
        MessageType: "RequestData",
        SenderID: getClientId(),
        MessageID: UUID.randomUUID().toString(),
        TargetID: sysId,
        AdditionalParameters: [JSONPath: jsonPaths]
    ]
    
    String body = JsonOutput.toJson(message)
    
    if (logEnable) log.debug "Cloud RequestData: ${jsonPaths}"
    
    Map params = [
        uri: CLOUD_REQUESTDATA_URL,
        requestContentType: "application/json",
        contentType: "application/json",
        body: body,
        timeout: 30,
        headers: [
            "Authorization": state.loginBearerToken,
            "User-Agent": USER_AGENT,
            "Accept": "*/*"
        ]
    ]
    
    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "Cloud RequestData successful"
            } else {
                log.warn "Cloud RequestData failed with status: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Cloud RequestData exception: ${e.message}"
    }
}

String getClientId() {
    String appId = state.appId ?: DEFAULT_CLOUD_APP_ID
    if (state.isLocalConnection) {
        return appId
    }
    return "${appId}_${settings.email}"
}

void disconnectCloud() {
    log.info "Disconnecting from Lennox cloud..."
    
    if (!state.loginBearerToken) {
        return
    }
    
    Map params = [
        uri: CLOUD_LOGOUT_URL,
        requestContentType: "application/json",
        contentType: "application/json",
        timeout: 10,
        headers: [
            "Authorization": state.loginBearerToken,
            "User-Agent": USER_AGENT,
            "Accept": "*/*"
        ]
    ]
    
    try {
        httpPost(params) { resp ->
            if (logEnable) log.debug "Cloud logout response: ${resp.status}"
        }
    } catch (Exception e) {
        log.warn "Cloud logout exception: ${e.message}"
    }
    
    state.loginBearerToken = null
    state.authBearerToken = null
    state.loginToken = null
}

// Subscribe to data
void subscribe() {
    if (!state.connected) {
        log.warn "Cannot subscribe - not connected"
        return
    }
    
    log.info "Subscribing to Lennox data..."
    Set<String> pathGroups = getRequiredPathGroups()
    
    if (state.isLocalConnection) {
        String paths1, paths2
        if (pathGroups == null) {
            if (logEnable) log.debug "RequestData mode: discovery (full paths for initial setup)"
            paths1 = "1;/systemControl;/systemController;/reminderSensors;/reminders;/alerts/active;/alerts/meta;/bleProvisionDB;/ble;/indoorAirQuality;/fwm;/rgw;/devices;/zones;/equipments;/schedules;/occupancy;/system"
            paths2 = "1;/automatedTest;/zoneTestControl;/homes;/reminders;/algorithm;/historyReportFileDetails;/interfaces;/logs"
        } else {
            if (logEnable) log.debug "RequestData mode: dynamic | path groups: ${pathGroups.sort().join(', ')}"
            paths1 = buildLocalPaths1(pathGroups)
            paths2 = buildLocalPaths2(pathGroups)
            if (logEnable) log.debug "RequestData paths1: ${paths1}"
            if (logEnable) log.debug "RequestData paths2: ${paths2}"
        }
        requestData(paths1)
        runIn(2, "requestDataSecondary", [data: paths2])
        runIn(3, "startMessagePump")
    } else {
        subscribeCloud(pathGroups)
    }
}

void requestDataSecondary(String paths) {
    requestData(paths)
}

void resubscribeForChildDevices() {
    if (!state.connected) return
    Set<String> pathGroups = getRequiredPathGroups()
    if (state.isLocalConnection) {
        String paths1, paths2
        if (pathGroups == null) {
            if (logEnable) log.debug "Re-subscribe RequestData mode: discovery"
            paths1 = "1;/systemControl;/systemController;/reminderSensors;/reminders;/alerts/active;/alerts/meta;/bleProvisionDB;/ble;/indoorAirQuality;/fwm;/rgw;/devices;/zones;/equipments;/schedules;/occupancy;/system"
            paths2 = "1;/automatedTest;/zoneTestControl;/homes;/reminders;/algorithm;/historyReportFileDetails;/interfaces;/logs"
        } else {
            if (logEnable) log.debug "Re-subscribe RequestData mode: dynamic | path groups: ${pathGroups.sort().join(', ')}"
            paths1 = buildLocalPaths1(pathGroups)
            paths2 = buildLocalPaths2(pathGroups)
            if (logEnable) log.debug "Re-subscribe RequestData paths1: ${paths1} | paths2: ${paths2}"
        }
        requestData(paths1)
        runIn(2, "requestDataSecondary", [data: paths2])
    } else {
        String cloudPaths1, cloudPaths2
        if (pathGroups == null) {
            if (logEnable) log.debug "Re-subscribe RequestData mode: discovery"
            cloudPaths1 = "1;/zones;/occupancy;/schedules;/reminderSensors;/reminders;/alerts/active;"
            cloudPaths2 = "1;/alerts/meta;/dealers;/devices;/equipments;/system;/fwm;/ocst;"
        } else {
            if (logEnable) log.debug "Re-subscribe RequestData mode: dynamic | path groups: ${pathGroups.sort().join(', ')}"
            cloudPaths1 = buildCloudPaths1(pathGroups)
            cloudPaths2 = buildCloudPaths2(pathGroups)
            if (logEnable) log.debug "Re-subscribe RequestData cloud paths1: ${cloudPaths1} | paths2: ${cloudPaths2}"
        }
        state.cloudPaths2 = cloudPaths2
        state.systems?.each { system ->
            cloudRequestData(system.sysId, cloudPaths1)
            runIn(2, "cloudRequestDataSecondary", [data: system.sysId])
        }
    }
    if (logEnable) log.debug "Re-subscribed RequestData for current child devices"
}

void requestData(String jsonPaths) {
    if (!state.connected) return
    
    String sysId = state.sysId ?: "LCC"
    String appId = state.appId ?: DEFAULT_LOCAL_APP_ID
    
    String url = "https://${settings.ipAddress}/Messages/RequestData"
    
    Map message = [
        MessageType: "RequestData",
        SenderID: appId,
        MessageID: UUID.randomUUID().toString(),
        TargetID: sysId,
        AdditionalParameters: [JSONPath: jsonPaths]
    ]
    
    String body = JsonOutput.toJson(message)
    
    if (logEnable) log.debug "RequestData: ${jsonPaths}"
    
    Map params = [
        uri: url,
        ignoreSSLIssues: true,
        contentType: "application/json",
        body: body,
        timeout: 30
    ]
    
    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "RequestData successful"
            } else {
                log.warn "RequestData failed with status: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "RequestData exception: ${e.message}"
    }
}

// Dynamic subscription: path groups required by installed child devices
Set<String> getInstalledChildKeys() {
    String baseDni = device.deviceNetworkId
    String prefix = baseDni + "-"
    Set<String> keys = [] as Set
    getChildDevices()?.each { child ->
        String dni = child.deviceNetworkId
        if (dni?.startsWith(prefix)) {
            String key = dni.substring(prefix.length())
            if (key) keys << key
        }
    }
    return keys
}

/** Returns required path groups (Set of names), or null when bootstrap (use full discovery paths). */
Set<String> getRequiredPathGroups() {
    if (!state.systemInitialized || !state.zones) return null
    Set<String> keys = getInstalledChildKeys()
    Set<String> groups = ["system", "zones", "schedules", "systemControl", "systemController", "devices", "reminderSensors", "reminders", "fwm"] as Set
    if (keys.any { it.startsWith("switch-manualAway") || it.startsWith("switch-smartAwayEnable") || it == "sensor-homeState" }) groups << "occupancy"
    if (keys.any { it == "sensor-activeAlerts" }) groups << "alerts"
    boolean needEquipments = keys.any { it.startsWith("zone-") || it.startsWith("sensor-diag-") }
    if (needEquipments) groups << "equipments"
    if (keys.any { it == "sensor-iaqPm25" || it == "sensor-iaqVoc" || it == "sensor-iaqCo2" }) groups << "indoorAirQuality"
    if (keys.any { it.startsWith("sensor-ble-") }) groups << "ble"
    if (keys.any { it == "sensor-wifiRssi" }) groups << "interfaces"
    if (keys.any { it == "sensor-internetStatus" || it == "sensor-relayStatus" }) groups << "rgw"
    return groups
}

String buildLocalPaths1(Set<String> pathGroups) {
    List<String> p = ["1"]
    p << "systemControl" << "systemController" << "reminderSensors" << "reminders"
    if (pathGroups.contains("alerts")) p << "alerts/active" << "alerts/meta"
    if (pathGroups.contains("ble")) p << "bleProvisionDB" << "ble"
    if (pathGroups.contains("indoorAirQuality")) p << "indoorAirQuality"
    p << "fwm"
    if (pathGroups.contains("rgw")) p << "rgw"
    p << "devices" << "zones"
    if (pathGroups.contains("equipments")) p << "equipments"
    p << "schedules"
    if (pathGroups.contains("occupancy")) p << "occupancy"
    p << "system"
    return p.collect { "/${it}" }.join(";") + ";"
}

String buildLocalPaths2(Set<String> pathGroups) {
    List<String> p = ["1"]
    if (pathGroups.contains("interfaces")) p << "interfaces"
    if (p.size() == 1) p << "reminders"
    return p.collect { "/${it}" }.join(";") + ";"
}

String buildCloudPaths1(Set<String> pathGroups) {
    List<String> p = ["1", "zones", "schedules", "reminderSensors", "reminders"]
    if (pathGroups.contains("occupancy")) p << "occupancy"
    if (pathGroups.contains("alerts")) p << "alerts/active"
    return p.collect { "/${it}" }.join(";") + ";"
}

String buildCloudPaths2(Set<String> pathGroups) {
    List<String> p = ["1"]
    if (pathGroups.contains("alerts")) p << "alerts/meta"
    p << "dealers" << "devices"
    if (pathGroups.contains("equipments")) p << "equipments"
    p << "system" << "fwm" << "ocst"
    return p.collect { "/${it}" }.join(";") + ";"
}

// Message pump - polls for messages
void startMessagePump() {
    if (!state.connected) {
        log.warn "Cannot start message pump - not connected"
        return
    }
    
    log.info "Starting message pump..."
    messagePump()
}

void messagePump() {
    if (!state.connected) return
    
    String appId = state.appId ?: (state.isLocalConnection ? DEFAULT_LOCAL_APP_ID : DEFAULT_CLOUD_APP_ID)
    Integer timeout = settings.longPollTimeout ?: 15
    
    if (state.isLocalConnection) {
        // Cap long poll so we don't hold the connection longer than the gap between polls;
        // otherwise the S30 can be overwhelmed and delay delivering updates for minutes.
        Integer pollIntervalSec = (settings.pollInterval ?: "30").toInteger()
        timeout = Math.min(timeout, pollIntervalSec)
    }
    
    String url
    Map params
    
    if (state.isLocalConnection) {
        // Local connection: GET with query only, no body and no Content-Type to avoid 400
        // (Lennox often returns 400 when client sends Content-Length/Transfer-Encoding quirks)
        url = "https://${settings.ipAddress}/Messages/${appId}/Retrieve"
        params = [
            uri: url,
            ignoreSSLIssues: true,
            query: [
                Direction: "Oldest-to-Newest",
                MessageCount: "10",
                StartTime: "1",
                LongPollingTimeout: timeout.toString()
            ],
            timeout: timeout + 15
        ]
    } else {
        // Cloud connection - no long polling
        url = CLOUD_RETRIEVE_URL
        params = [
            uri: url,
            requestContentType: "application/json",
            contentType: "application/json",
            query: [
                Direction: "Oldest-to-Newest",
                MessageCount: "10",
                StartTime: "1",
                LongPollingTimeout: "0"  // Cloud doesn't support long polling
            ],
            headers: [
                "Authorization": state.loginBearerToken,
                "User-Agent": USER_AGENT,
                "Accept": "*/*"
            ],
            timeout: 30
        ]
    }
    
    try {
        asynchttpGet("handleMessagePumpResponse", params)
    } catch (Exception e) {
        log.error "Message pump exception: ${e.message}"
        scheduleNextPoll()
    }
}

void handleMessagePumpResponse(resp, data) {
    if (!state.connected) return
    
    try {
        if (resp.status == 200) {
            String body = resp.getData()
            if (body) {
                def json = new JsonSlurper().parseText(body)
                if (json?.messages && json.messages.size() > 0) {
                    List batch = json.messages
                    if (logEnable) log.debug "Retrieve: ${batch.size()} message(s), response size: ${body.size()} chars"
                    // Defer processing so we return quickly and schedule the next poll immediately.
                    if (state.pendingMessageBatch != null) {
                        state.queuedMessageBatch = batch
                    } else {
                        state.pendingMessageBatch = batch
                        runIn(0, "processPendingMessageBatch")
                    }
                }
            }
        } else if (resp.status == 204) {
            // No messages - this is normal
            if (logEnable) log.debug "No messages available"
        } else if (resp.status == 401) {
            log.error "Unauthorized - reconnecting..."
            state.connected = false
            sendEvent(name: "connectionState", value: STATE_LOGIN_FAILED)
            scheduleReconnect()
            return
        } else {
            // 400 is often returned by Lennox due to server header quirks (e.g. Content-Length + Transfer-Encoding). Log at debug to avoid log spam; pump continues.
            String errBody = ""
            try { errBody = resp.getData() ?: "" } catch (Exception ignored) {}
            if (errBody) errBody = errBody.take(200) + (errBody.size() > 200 ? "..." : "")
            if (resp.status == 400) {
                if (logEnable) log.debug "Message pump response status: 400${errBody ? " - ${errBody}" : ""}"
            } else {
                log.warn "Message pump response status: ${resp.status}${errBody ? " - ${errBody}" : ""}"
            }
        }
    } catch (Exception e) {
        log.error "Error processing message pump response: ${e.message}"
    }
    
    scheduleNextPoll()
}

void processPendingMessageBatch() {
    if (!state.connected) return
    List batch = state.pendingMessageBatch
    if (!batch) return
    
    try {
        if (logEnable) log.debug "Processing batch: ${batch.size()} message(s)"
        batch.each { message ->
            processMessage(message)
        }
        state.messageCount = (state.messageCount ?: 0) + batch.size()
        sendEvent(name: "messageCount", value: state.messageCount)
        sendEvent(name: "lastMessageTime", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
        autoCreateChildDevices()
    } catch (Exception e) {
        log.error "Error processing message batch: ${e.message}"
    }
    
    state.pendingMessageBatch = null
    if (state.queuedMessageBatch != null) {
        state.pendingMessageBatch = state.queuedMessageBatch
        state.queuedMessageBatch = null
        runIn(0, "processPendingMessageBatch")
    }
}

void scheduleNextPoll() {
    if (!state.connected) return
    
    Integer interval
    if (state.fastPollRemaining > 0) {
        state.fastPollRemaining = state.fastPollRemaining - 1
        interval = ((settings.fastPollInterval ?: 0.75) * 1000).toInteger()
        runInMillis(interval, "messagePump")
    } else if (!isDiscoveryComplete()) {
        // Faster polling during initial setup until zones/children are discovered
        interval = state.isLocalConnection ? DISCOVERY_POLL_INTERVAL_LOCAL_SEC : DISCOVERY_POLL_INTERVAL_CLOUD_SEC
        runIn(interval, "messagePump")
    } else {
        interval = (settings.pollInterval ?: (state.isLocalConnection ? "30" : "60")).toInteger()
        runIn(interval, "messagePump")
    }
}

void scheduleReconnect() {
    Integer backoff = state.reconnectBackoff ?: 60
    log.info "Scheduling reconnect in ${backoff} seconds..."
    sendEvent(name: "connectionState", value: STATE_RETRY_WAIT)
    runIn(backoff, "connect")
    state.reconnectBackoff = Math.min(backoff * 2, 900)
}

void resetReconnectBackoff() {
    state.reconnectBackoff = 60
}

void triggerFastPoll() {
    state.fastPollRemaining = settings.fastPollCount ?: 10
}

// Message processing
void processMessage(Map message) {
    String sysId = message.SenderID ?: message.SenderId
    
    if (sysId && sysId != state.appId && !state.sysId) {
        state.sysId = sysId
    }
    
    if (logEnable) log.debug "Processing message from: ${sysId}"
    
    if (message.Data) {
        Map data = message.Data
        if (logEnable) log.debug "Message Data keys: ${data.keySet().sort().join(', ')}"
        
        // Process system messages
        if (data.system) {
            processSystemMessage(data.system)
        }
        
        // Process zones
        if (data.zones) {
            processZonesMessage(data.zones)
        }
        
        // Process schedules
        if (data.schedules) {
            processSchedulesMessage(data.schedules)
        }
        
        // Process occupancy (away mode)
        if (data.occupancy) {
            processOccupancyMessage(data.occupancy)
        }
        
        // Process devices
        if (data.devices) {
            processDevicesMessage(data.devices)
        }
        
        // Process equipment
        if (data.equipments) {
            processEquipmentsMessage(data.equipments)
        }
        
        // Process alerts
        if (data.alerts) {
            processAlertsMessage(data.alerts)
        }
        
        // Process interfaces (WiFi status)
        if (data.interfaces) {
            processInterfacesMessage(data.interfaces)
        }
        
        // Process rgw (internet status)
        if (data.rgw) {
            processRgwMessage(data.rgw)
        }
        
        // Process indoor air quality
        if (data.indoorAirQuality) {
            processIaqMessage(data.indoorAirQuality)
        }
        
        // Process BLE devices
        if (data.ble) {
            processBleMessage(data.ble)
        }
        
        // Process weather
        if (data.weather) {
            processWeatherMessage(data.weather)
        }
    }
}

void processSystemMessage(Map system) {
    if (logEnable) log.debug "Processing system message"
    
    state.systemInitialized = true
    
    if (system.config) {
        Map config = system.config
        
        if (config.name) {
            sendEventIfChanged("systemName", config.name)
            state.systemName = config.name
        }
        if (config.temperatureUnit) state.temperatureUnit = config.temperatureUnit
        if (config.allergenDefender != null) {
            sendEventIfChanged("allergenDefender", config.allergenDefender.toString())
            state.allergenDefender = config.allergenDefender
        }
        if (config.ventilationMode) {
            sendEventIfChanged("ventilationMode", config.ventilationMode)
            state.ventilationMode = config.ventilationMode
        }
        if (config.centralMode != null) state.centralMode = config.centralMode
        if (config.circulateTime) state.circulateTime = config.circulateTime
        if (config.dehumidificationMode) state.dehumidificationMode = config.dehumidificationMode
        if (config.humidificationMode) state.humidificationMode = config.humidificationMode

        updateSystemSwitches()
        
        if (config.options) {
            Map options = config.options
            if (options.indoorUnitType) state.indoorUnitType = options.indoorUnitType
            if (options.outdoorUnitType) state.outdoorUnitType = options.outdoorUnitType
            if (options.productType) {
                sendEventIfChanged("productType", options.productType)
                state.productType = options.productType
            }
            if (options.humidifierType) state.humidifierType = options.humidifierType
            if (options.dehumidifierType) state.dehumidifierType = options.dehumidifierType
            if (options.ventilation) {
                state.ventilationUnitType = options.ventilation.unitType
                state.ventilationControlMode = options.ventilation.controlMode
            }
        }
    }
    
    if (system.status) {
        Map status = system.status
        
        if (status.outdoorTemperature != null) {
            def temp = convertTemperature(status.outdoorTemperature, status.outdoorTemperatureC)
            sendEventIfChanged("outdoorTemperature", temp, getTemperatureScale())
            state.outdoorTemperature = temp
        }
        if (status.outdoorTemperatureStatus) {
            sendEventIfChanged("outdoorTemperatureStatus", status.outdoorTemperatureStatus)
        }
        if (status.numberOfZones) {
            sendEventIfChanged("numberOfZones", status.numberOfZones)
            state.numberOfZones = status.numberOfZones
        }
        if (status.zoningMode) {
            sendEventIfChanged("zoningMode", status.zoningMode)
            state.zoningMode = status.zoningMode
        }
        if (status.diagLevel != null) state.diagLevel = status.diagLevel
    }
    
    if (!state.childDevicesCreated) state.childDevicesCreated = true
}

void processZonesMessage(List zones) {
    if (logEnable) log.debug "Processing ${zones.size()} zones"
    state.zonesDiscovered = true
    
    zones.each { zone ->
        Integer zoneId = zone.id
        
        // Initialize zone state if needed
        if (!state.zones[zoneId.toString()]) {
            state.zones[zoneId.toString()] = [:]
        }
        Map zoneState = state.zones[zoneId.toString()]
        
        // Process zone config
        if (zone.config) {
            Map config = zone.config
            if (config.name) zoneState.name = config.name
            if (config.heatingOption != null) zoneState.heatingOption = config.heatingOption
            if (config.coolingOption != null) zoneState.coolingOption = config.coolingOption
            if (config.emergencyHeatingOption != null) zoneState.emergencyHeatingOption = config.emergencyHeatingOption
            if (config.humidificationOption != null) zoneState.humidificationOption = config.humidificationOption
            if (config.dehumidificationOption != null) zoneState.dehumidificationOption = config.dehumidificationOption
            if (config.maxCsp) zoneState.maxCsp = config.maxCsp
            if (config.minCsp) zoneState.minCsp = config.minCsp
            if (config.maxHsp) zoneState.maxHsp = config.maxHsp
            if (config.minHsp) zoneState.minHsp = config.minHsp
            if (config.maxCspC) zoneState.maxCspC = config.maxCspC
            if (config.minCspC) zoneState.minCspC = config.minCspC
            if (config.maxHspC) zoneState.maxHspC = config.maxHspC
            if (config.minHspC) zoneState.minHspC = config.minHspC
            if (config.maxHumSp) zoneState.maxHumSp = config.maxHumSp
            if (config.minHumSp) zoneState.minHumSp = config.minHumSp
            if (config.maxDehumSp) zoneState.maxDehumSp = config.maxDehumSp
            if (config.minDehumSp) zoneState.minDehumSp = config.minDehumSp
            if (config.scheduleId != null) zoneState.scheduleId = config.scheduleId
        }
        
        // Process zone status
        if (zone.status) {
            Map status = zone.status
            if (status.temperature != null) {
                zoneState.temperature = status.temperature
                zoneState.temperatureC = status.temperatureC
            }
            if (status.humidity != null) zoneState.humidity = status.humidity
            if (status.temperatureStatus) zoneState.temperatureStatus = status.temperatureStatus
            if (status.humidityStatus) zoneState.humidityStatus = status.humidityStatus
            if (status.tempOperation) zoneState.tempOperation = status.tempOperation
            if (status.humOperation) zoneState.humOperation = status.humOperation
            if (status.damper != null) zoneState.damper = status.damper
            if (status.fan != null) zoneState.fan = status.fan
            if (status.demand != null) zoneState.demand = status.demand
            if (status.allergenDefender != null) zoneState.allergenDefender = status.allergenDefender
            if (status.ventilation != null) zoneState.ventilation = status.ventilation
            if (status.heatCoast != null) zoneState.heatCoast = status.heatCoast
            if (status.defrost != null) zoneState.defrost = status.defrost
            if (status.aux != null) zoneState.aux = status.aux
            if (status.coolCoast != null) zoneState.coolCoast = status.coolCoast
            
            // Process period (setpoints and modes)
            if (status.period) {
                Map period = status.period
                if (period.systemMode) zoneState.systemMode = period.systemMode
                if (period.fanMode) zoneState.fanMode = period.fanMode
                if (period.humidityMode) zoneState.humidityMode = period.humidityMode
                if (period.csp != null) zoneState.csp = period.csp
                if (period.hsp != null) zoneState.hsp = period.hsp
                if (period.cspC != null) zoneState.cspC = period.cspC
                if (period.hspC != null) zoneState.hspC = period.hspC
                if (period.sp != null) zoneState.sp = period.sp
                if (period.spC != null) zoneState.spC = period.spC
                if (period.husp != null) zoneState.husp = period.husp
                if (period.desp != null) zoneState.desp = period.desp
            }
        }
        
        // Some messages (e.g. from Lennox app updates) send status-like fields at zone top level; merge them
        if (zone.temperature != null) {
            zoneState.temperature = zone.temperature
            zoneState.temperatureC = zone.temperatureC
        }
        if (zone.humidity != null) zoneState.humidity = zone.humidity
        if (zone.tempOperation != null) zoneState.tempOperation = zone.tempOperation
        if (zone.humOperation != null) zoneState.humOperation = zone.humOperation
        if (zone.damper != null) zoneState.damper = zone.damper
        if (zone.fan != null) zoneState.fan = zone.fan
        if (zone.demand != null) zoneState.demand = zone.demand
        if (zone.systemMode != null) zoneState.systemMode = zone.systemMode
        if (zone.fanMode != null) zoneState.fanMode = zone.fanMode
        if (zone.humidityMode != null) zoneState.humidityMode = zone.humidityMode
        if (zone.csp != null) zoneState.csp = zone.csp
        if (zone.hsp != null) zoneState.hsp = zone.hsp
        if (zone.cspC != null) zoneState.cspC = zone.cspC
        if (zone.hspC != null) zoneState.hspC = zone.hspC
        if (zone.sp != null) zoneState.sp = zone.sp
        if (zone.spC != null) zoneState.spC = zone.spC
        if (zone.husp != null) zoneState.husp = zone.husp
        if (zone.desp != null) zoneState.desp = zone.desp
        if (zone.scheduleName != null) zoneState.scheduleName = zone.scheduleName
        if (zone.scheduleId != null) zoneState.scheduleId = zone.scheduleId
        if (zone.name != null) zoneState.name = zone.name
        
        // Create or update child thermostat device
        if (zoneState.name && zoneState.temperature != null) {
            createOrUpdateZoneThermostat(zoneId, zoneState)
        }
    }
}

void processSchedulesMessage(List schedules) {
    schedules.each { schedule ->
        Integer scheduleId = schedule.id
        if (schedule.schedule) {
            Map sched = schedule.schedule
            if (sched.name) {
                if (!state.schedules) state.schedules = [:]
                state.schedules[scheduleId.toString()] = [
                    id: scheduleId,
                    name: sched.name
                ]
            }
        }
    }
}

void processOccupancyMessage(Map occupancy) {
    if (occupancy.manualAway != null) {
        sendEventIfChanged("manualAwayMode", occupancy.manualAway.toString())
        state.manualAwayMode = occupancy.manualAway
        
        // Update away mode switch if exists
        updateAwayModeSwitches()
    }
    
    if (occupancy.smartAway) {
        Map smartAway = occupancy.smartAway
        if (smartAway.config) {
            if (smartAway.config.enabled != null) {
                sendEventIfChanged("smartAwayEnabled", smartAway.config.enabled.toString())
                state.smartAwayEnabled = smartAway.config.enabled
            }
            if (smartAway.config.cancel != null) state.smartAwayCancel = smartAway.config.cancel
            if (smartAway.config.reset != null) state.smartAwayReset = smartAway.config.reset
        }
        if (smartAway.status) {
            if (smartAway.status.state) state.smartAwayState = smartAway.status.state
            if (smartAway.status.setpointState) state.smartAwaySetpointState = smartAway.status.setpointState
        }
    }
}

void processDevicesMessage(List devices) {
    devices.each { dev ->
        if (dev.device?.deviceType == 500) {
            dev.device.features?.each { feature ->
                if (feature.feature?.fid == 9) {
                    String serialNumber = feature.feature.values[0].value
                    sendEventIfChanged("serialNumber", serialNumber)
                    state.serialNumber = serialNumber
                }
                if (feature.feature?.fid == 11) {
                    String softwareVersion = feature.feature.values[0].value
                    sendEventIfChanged("softwareVersion", softwareVersion)
                    state.softwareVersion = softwareVersion
                }
            }
        }
    }
}

void processEquipmentsMessage(List equipments) {
    equipments.each { equipment ->
        Integer equipmentId = equipment.id
        if (!state.equipment) state.equipment = [:]
        if (!state.equipment[equipmentId.toString()]) {
            state.equipment[equipmentId.toString()] = [id: equipmentId]
        }
        Map eqState = state.equipment[equipmentId.toString()]
        
        if (equipment.equipment) {
            Map eq = equipment.equipment
            if (eq.equipType) eqState.equipType = eq.equipType
            
            eq.features?.each { feature ->
                if (feature.feature?.fid == 15) {
                    eqState.equipmentTypeName = feature.feature.values[0].value
                }
                if (feature.feature?.fid == 6) {
                    eqState.unitModelNumber = feature.feature.values[0].value
                }
                if (feature.feature?.fid == 7) {
                    eqState.unitSerialNumber = feature.feature.values[0].value
                }
            }
            
            eq.parameters?.each { parameter ->
                if (parameter.parameter?.pid == 525) {
                    state.singleSetpointMode = (parameter.parameter.value == 1 || parameter.parameter.value == "1")
                }
            }
            
            eq.diagnostics?.each { diagEntry ->
                Integer diagId = diagEntry.id
                if (diagEntry.diagnostic) {
                    Map diag = diagEntry.diagnostic
                    String key = "${equipmentId}_${diagId}"
                    
                    if (!state.equipmentDiagnostics) state.equipmentDiagnostics = [:]
                    if (!state.equipmentDiagnostics[key]) {
                        state.equipmentDiagnostics[key] = [equipmentId: equipmentId, diagId: diagId]
                    }
                    Map diagState = state.equipmentDiagnostics[key]
                    
                    if (diag.name) diagState.name = diag.name
                    if (diag.unit) diagState.unit = diag.unit
                    if (diag.containsKey("valid")) diagState.valid = diag.valid
                    if (diag.containsKey("value")) diagState.value = diag.value
                    
                    if (diagState.name) {
                        String diagDni = "${device.deviceNetworkId}-sensor-diag-${equipmentId}-${diagId}"
                        if (settings.createDiagnosticSensors == true || getChildDevice(diagDni)) {
                            updateDiagnosticSensor(equipmentId, diagId, diagState)
                        }
                    }
                }
            }
        }
    }
}

void processAlertsMessage(Map alerts) {
    if (alerts.active) {
        state.activeAlerts = []
        alerts.active.each { alert ->
            if (alert.alert?.code && alert.alert.code != 0) {
                state.activeAlerts.add([
                    code: alert.alert.code,
                    message: alert.alert.userMessage ?: "",
                    priority: alert.alert.priority,
                    isActive: alert.alert.isStillActive
                ])
            }
        }
    }
    if (alerts.meta) {
        state.alertsNumActive = alerts.meta.numActiveAlerts
    }
}

void processInterfacesMessage(List interfaces) {
    if (interfaces && interfaces.size() > 0) {
        Map info = interfaces[0]?.Info
        if (info?.status) {
            Map status = info.status
            if (status.rssi != null) {
                sendEventIfChanged("wifiRssi", status.rssi, "dBm")
                state.wifiRssi = status.rssi
            }
            if (status.ssid) state.wifiSsid = status.ssid
            if (status.ip) state.wifiIp = status.ip
            if (status.macAddr) state.wifiMacAddr = status.macAddr
        }
    }
}

void processRgwMessage(Map rgw) {
    if (rgw.status) {
        if (rgw.status.internetStatus != null) {
            sendEventIfChanged("internetStatus", rgw.status.internetStatus.toString())
            state.internetStatus = rgw.status.internetStatus
        }
        if (rgw.status.relayServerConnected != null) {
            sendEventIfChanged("relayServerConnected", rgw.status.relayServerConnected.toString())
            state.relayServerConnected = rgw.status.relayServerConnected
        }
    }
}

void processIaqMessage(Map iaq) {
    if (iaq.overall_index) state.iaqOverallIndex = iaq.overall_index
    if (iaq.mitigation_action) state.iaqMitigationAction = iaq.mitigation_action
    if (iaq.mitigation_state) state.iaqMitigationState = iaq.mitigation_state
    
    iaq.sensor?.each { sensor ->
        String name = sensor.name?.toLowerCase()
        if (name in ["pm25", "voc", "co2"]) {
            if (!state.iaqSensors) state.iaqSensors = [:]
            state.iaqSensors[name] = [
                value: sensor.value,
                score: sensor.component_score,
                sta: sensor.sta,
                lta: sensor.lta
            ]
        }
    }
}

void processBleMessage(Map ble) {
    ble.devices?.each { dev ->
        if (dev.device?.wdn && dev.device.wdn != 0) {
            Integer bleId = dev.device.wdn
            if (!state.bleDevices) state.bleDevices = [:]
            if (!state.bleDevices[bleId.toString()]) {
                state.bleDevices[bleId.toString()] = [id: bleId]
            }
            Map bleState = state.bleDevices[bleId.toString()]
            
            Map d = dev.device
            if (d.deviceName) bleState.deviceName = d.deviceName
            if (d.deviceModel) bleState.deviceModel = d.deviceModel
            if (d.commStatus) bleState.commStatus = d.commStatus
            if (d.inputs) bleState.inputs = d.inputs
        }
    }
}

void processWeatherMessage(Map weather) {
    if (weather.status) {
        Map status = weather.status
        state.weatherValid = status.isValid
        
        if (status.env) {
            Map env = status.env
            state.weatherAirQuality = env.airQuality
            state.weatherTree = env.tree
            state.weatherWeed = env.weed
            state.weatherGrass = env.grass
            state.weatherMold = env.mold
            state.weatherUvIndex = env.uvIndex
            state.weatherHumidity = env.humidity
            state.weatherWindSpeed = env.windSpeed
            state.weatherDewpoint = env.Dewpoint
        }
    }
}

// Child device management
void createOrUpdateZoneThermostat(Integer zoneId, Map zoneState) {
    String dni = "${device.deviceNetworkId}-zone-${zoneId}"
    def child = getChildDevice(dni)
    
    if (!child) {
        log.info "Creating zone thermostat: ${zoneState.name} (Zone ${zoneId})"
        try {
            child = addChildDevice(
                "rbyrbt",
                "Lennox iComfort Child Zone Thermostat",
                dni,
                [
                    name: "Lennox Zone ${zoneId}",
                    label: zoneState.name ?: "Lennox Zone ${zoneId}",
                    isComponent: false
                ]
            )
            child.updateDataValue("zoneId", zoneId.toString())
        } catch (Exception e) {
            log.error "Failed to create zone thermostat: ${e.message}"
            return
        }
    }
    
    // Resolve schedule name from stored schedules
    if (zoneState.scheduleId != null && state.schedules) {
        Map sched = state.schedules[zoneState.scheduleId.toString()]
        if (sched?.name) {
            zoneState.scheduleName = sched.name
        }
    }
    
    // Update child device with zone data
    if (child) {
        child.updateZoneState(zoneState, state.singleSetpointMode ?: false)
    }
}

void updateAwayModeSwitches() {
    def manualAwayChild = getChildDevice("${device.deviceNetworkId}-switch-manualAway")
    if (manualAwayChild) manualAwayChild.updateSwitchState(state.manualAwayMode ?: false)

    def smartAwayChild = getChildDevice("${device.deviceNetworkId}-switch-smartAwayEnable")
    if (smartAwayChild) smartAwayChild.updateSwitchState(state.smartAwayEnabled ?: false)
}

void updateSystemSwitches() {
    String baseDni = device.deviceNetworkId

    def allergenChild = getChildDevice("${baseDni}-switch-allergenDefender")
    if (allergenChild) allergenChild.updateSwitchState(state.allergenDefender ?: false)

    def ventilationChild = getChildDevice("${baseDni}-switch-ventilation")
    if (ventilationChild) {
        Boolean ventOn = state.ventilationMode == "on"
        ventilationChild.updateSwitchState(ventOn)
    }

    def zoningChild = getChildDevice("${baseDni}-switch-zoning")
    if (zoningChild) zoningChild.updateSwitchState(state.centralMode ?: false)
}

// Create switch child devices
void createSwitchDevices() {
    if (settings.createSwitches == false) return
    
    // Ventilation switch
    if (state.ventilationUnitType) {
        createSwitchDevice("ventilation", "Ventilation")
    }
    
    // Allergen Defender switch
    if (state.allergenDefender != null) {
        createSwitchDevice("allergenDefender", "Allergen Defender")
    }
    
    // Away mode switches
    createSwitchDevice("manualAway", "Manual Away Mode")
    createSwitchDevice("smartAwayEnable", "Smart Away")
    
    // Zoning switch (only for multi-zone systems)
    if (state.numberOfZones && state.numberOfZones > 1) {
        createSwitchDevice("zoning", "Zoning Mode")
    }
    
    // Parameter safety switch
    createSwitchDevice("parameterSafety", "Parameter Safety")
}

void createSwitchDevice(String switchType, String label) {
    String dni = "${device.deviceNetworkId}-switch-${switchType}"
    def child = getChildDevice(dni)
    
    if (!child) {
        log.info "Creating switch: ${label}"
        String driverName = (switchType == "parameterSafety") ? "Lennox iComfort Child Switch - Parameter Safety" : "Lennox iComfort Child Switch"
        try {
            child = addChildDevice(
                "rbyrbt",
                driverName,
                dni,
                [
                    name: "Lennox ${label}",
                    label: "${state.systemName ?: 'Lennox'} ${label}",
                    isComponent: false
                ]
            )
            child.updateDataValue("switchType", switchType)
            child.sendEvent(name: "switchType", value: switchType)
            initializeSwitchState(child, switchType)
        } catch (Exception e) {
            log.error "Failed to create switch ${label}: ${e.message}"
        }
    }
}

private void initializeSwitchState(child, String switchType) {
    Boolean switchState = false
    switch (switchType) {
        case "allergenDefender":
            switchState = state.allergenDefender ?: false
            break
        case "ventilation":
            switchState = (state.ventilationMode == "on")
            break
        case "zoning":
            switchState = state.centralMode ?: false
            break
        case "manualAway":
            switchState = state.manualAwayMode ?: false
            break
        case "smartAwayEnable":
            switchState = state.smartAwayEnabled ?: false
            break
        case "parameterSafety":
            switchState = false
            break
    }
    child.updateSwitchState(switchState)
}

// Create sensor child devices
void createSensorDevices() {
    if (settings.createSensors == false) return
    
    // Outdoor temperature sensor
    if (state.outdoorTemperature != null) {
        createSensorDevice("outdoorTemperature", "Outdoor Temperature")
        updateSensorDevice("outdoorTemperature", [tempF: state.outdoorTemperature, tempC: state.outdoorTemperatureC])
    }
    
    // WiFi RSSI sensor (local connections only)
    if (state.isLocalConnection && state.wifiRssi != null) {
        createSensorDevice("wifiRssi", "WiFi Signal")
        updateSensorDevice("wifiRssi", [rssi: state.wifiRssi])
    }
    
    // Alerts sensor
    createSensorDevice("activeAlerts", "Active Alerts")
    updateSensorDevice("activeAlerts", [count: state.alertsNumActive ?: 0, text: "No alerts", priority: "none"])
    
    // Home state (presence) sensor
    createSensorDevice("homeState", "Home State")
    Boolean isHome = !(state.manualAwayMode ?: false)
    updateSensorDevice("homeState", [isHome: isHome])
    
    // Internet status sensor (local only)
    if (state.isLocalConnection) {
        createSensorDevice("internetStatus", "Internet Status")
        updateSensorDevice("internetStatus", [status: state.internetStatus?.toString() ?: "unknown"])
        
        createSensorDevice("relayStatus", "Relay Server Status")
        updateSensorDevice("relayStatus", [status: state.relayServerConnected?.toString() ?: "unknown"])
    }
    
    // IAQ sensors (if available)
    if (state.iaqSensors) {
        if (state.iaqSensors.pm25) {
            createSensorDevice("iaqPm25", "IAQ PM2.5")
            updateSensorDevice("iaqPm25", state.iaqSensors.pm25)
        }
        if (state.iaqSensors.voc) {
            createSensorDevice("iaqVoc", "IAQ VOC")
            updateSensorDevice("iaqVoc", state.iaqSensors.voc)
        }
        if (state.iaqSensors.co2) {
            createSensorDevice("iaqCo2", "IAQ CO2")
            updateSensorDevice("iaqCo2", state.iaqSensors.co2)
        }
    }
    
    // BLE devices
    state.bleDevices?.each { bleId, bleState ->
        String sensorType = "ble"
        String dni = "${device.deviceNetworkId}-sensor-ble-${bleId}"
        def child = getChildDevice(dni)
        
        if (!child) {
            String label = bleState.deviceName ?: "BLE Device ${bleId}"
            log.info "Creating BLE sensor: ${label}"
            try {
                child = addChildDevice(
                    "rbyrbt",
                    "Lennox iComfort Child Sensor",
                    dni,
                    [
                        name: "Lennox ${label}",
                        label: label,
                        isComponent: false
                    ]
                )
                child.updateDataValue("sensorType", sensorType)
                child.sendEvent(name: "sensorType", value: sensorType)
                child.updateDataValue("bleId", bleId.toString())
            } catch (Exception e) {
                log.error "Failed to create BLE sensor ${label}: ${e.message}"
            }
        }
        
        if (child) {
            child.updateBle(bleState.deviceName, bleState.deviceModel, bleState.commStatus, bleState.inputs)
        }
    }
}

void createSensorDevice(String sensorType, String label) {
    String dni = "${device.deviceNetworkId}-sensor-${sensorType}"
    def child = getChildDevice(dni)
    
    if (!child) {
        log.info "Creating sensor: ${label}"
        try {
            child = addChildDevice(
                "rbyrbt",
                "Lennox iComfort Child Sensor",
                dni,
                [
                    name: "Lennox ${label}",
                    label: "${state.systemName ?: 'Lennox'} ${label}",
                    isComponent: false
                ]
            )
            child.updateDataValue("sensorType", sensorType)
            child.sendEvent(name: "sensorType", value: sensorType)
        } catch (Exception e) {
            log.error "Failed to create sensor ${label}: ${e.message}"
        }
    }
}

void updateSensorDevice(String sensorType, Map data) {
    String dni = "${device.deviceNetworkId}-sensor-${sensorType}"
    def child = getChildDevice(dni)
    
    if (child) {
        child.updateSensorValue(data)
    }
}

void updateDiagnosticSensor(Integer equipmentId, Integer diagId, Map diagState) {
    String eqName = state.equipment?.get(equipmentId.toString())?.equipmentTypeName ?: "Equipment ${equipmentId}"
    String diagName = diagState.name
    String sensorKey = "diag-${equipmentId}-${diagId}"
    String dni = "${device.deviceNetworkId}-sensor-${sensorKey}"
    def child = getChildDevice(dni)
    
    if (!child) {
        String label = "${state.systemName ?: 'Lennox'} ${eqName} ${diagName}"
        log.info "Creating diagnostic sensor: ${label}"
        try {
            child = addChildDevice(
                "rbyrbt",
                "Lennox iComfort Child Sensor",
                dni,
                [
                    name: "Lennox Diag ${eqName} ${diagName}",
                    label: label,
                    isComponent: false
                ]
            )
            child.updateDataValue("sensorType", "diagnostic")
            child.sendEvent(name: "sensorType", value: "diagnostic")
            child.updateDataValue("equipmentId", equipmentId.toString())
            child.updateDataValue("diagnosticId", diagId.toString())
        } catch (Exception e) {
            log.error "Failed to create diagnostic sensor ${diagName}: ${e.message}"
            return
        }
    }
    
    if (child && diagState.containsKey("value")) {
        child.updateDiagnostic(diagState.name, diagState.value?.toString(), diagState.unit)
    }
}

void autoCreateChildDevices() {
    if (!state.systemInitialized) return
    // Run bulk create (from optionsPage flags) only once. After that, manageDevicesPage
    // is the source of truth: devices are added/removed only via createDeviceByKey/removeDeviceByKey.
    if (!state.initialChildCreationDone) {
        createSwitchDevices()
        createSensorDevices()
        state.initialChildCreationDone = true
    }
}

Boolean isDiscoveryComplete() {
    return state.systemInitialized && state.zonesDiscovered && state.childDevicesCreated
}

Map getChildDeviceInventory() {
    Map inventory = [zones: [], switches: [], sensors: [], diagnostics: []]
    String baseDni = device.deviceNetworkId
    Set installedDnis = (getChildDevices()?.collect { it.deviceNetworkId } ?: []) as Set

    state.zones?.each { zoneId, zoneState ->
        String dni = "${baseDni}-zone-${zoneId}"
        inventory.zones << [
            key: "zone-${zoneId}", dni: dni, label: zoneState.name ?: "Zone ${zoneId}",
            description: "Thermostat control for this zone (temperature, mode, fan, setpoints)",
            installed: installedDnis.contains(dni), category: "zone"
        ]
    }

    // Switches
    List switchDefs = [
        [key: "ventilation", label: "Ventilation", available: state.ventilationUnitType != null,
         description: "On/off control for the ventilator unit"],
        [key: "allergenDefender", label: "Allergen Defender", available: state.allergenDefender != null,
         description: "Enables Lennox Allergen Defender air filtration mode"],
        [key: "manualAway", label: "Manual Away Mode", available: true,
         description: "Sets the system to away mode for energy savings"],
        [key: "smartAwayEnable", label: "Smart Away", available: true,
         description: "Allows the system to automatically enter away mode via occupancy detection"],
        [key: "zoning", label: "Zoning Mode", available: (state.numberOfZones ?: 0) > 1,
         description: "Toggles central/zoned operation for multi-zone systems"],
        [key: "parameterSafety", label: "Parameter Safety", available: true,
         description: "Safety lock for equipment parameter changes (auto-disables after timeout)"],
    ]
    switchDefs.each { sw ->
        if (sw.available) {
            String dni = "${baseDni}-switch-${sw.key}"
            inventory.switches << [
                key: "switch-${sw.key}", dni: dni, label: sw.label,
                description: sw.description,
                installed: installedDnis.contains(dni), category: "switch"
            ]
        }
    }

    // Sensors
    List sensorDefs = [
        [key: "outdoorTemperature", label: "Outdoor Temperature", available: state.outdoorTemperature != null,
         description: "Outdoor temperature reported by the system"],
        [key: "wifiRssi", label: "WiFi Signal", available: state.isLocalConnection && state.wifiRssi != null,
         description: "Thermostat WiFi signal strength (local connections only)"],
        [key: "activeAlerts", label: "Active Alerts", available: true,
         description: "Count and details of active system alerts"],
        [key: "homeState", label: "Home State", available: true,
         description: "Home/away occupancy state as a presence sensor"],
        [key: "internetStatus", label: "Internet Status", available: state.isLocalConnection == true,
         description: "Whether the thermostat has internet connectivity"],
        [key: "relayStatus", label: "Relay Server Status", available: state.isLocalConnection == true,
         description: "Whether the thermostat is connected to the Lennox relay server"],
        [key: "iaqPm25", label: "IAQ PM2.5", available: state.iaqSensors?.pm25 != null,
         description: "Indoor air quality PM2.5 particulate reading"],
        [key: "iaqVoc", label: "IAQ VOC", available: state.iaqSensors?.voc != null,
         description: "Indoor air quality volatile organic compound reading"],
        [key: "iaqCo2", label: "IAQ CO2", available: state.iaqSensors?.co2 != null,
         description: "Indoor air quality CO\u2082 level reading"],
    ]
    sensorDefs.each { sn ->
        if (sn.available) {
            String dni = "${baseDni}-sensor-${sn.key}"
            inventory.sensors << [
                key: "sensor-${sn.key}", dni: dni, label: sn.label,
                description: sn.description,
                installed: installedDnis.contains(dni), category: "sensor"
            ]
        }
    }

    state.bleDevices?.each { bleId, bleState ->
        String dni = "${baseDni}-sensor-ble-${bleId}"
        inventory.sensors << [
            key: "sensor-ble-${bleId}", dni: dni, label: bleState.deviceName ?: "BLE Device ${bleId}",
            description: "Bluetooth temperature/humidity sensor",
            installed: installedDnis.contains(dni), category: "sensor"
        ]
    }

    state.equipmentDiagnostics?.each { diagKey, diagState ->
        Integer eqId = diagState.equipmentId
        Integer diagId = diagState.diagId
        String eqName = state.equipment?.get(eqId.toString())?.equipmentTypeName ?: "Equipment ${eqId}"
        String sensorKey = "diag-${eqId}-${diagId}"
        String dni = "${baseDni}-sensor-${sensorKey}"
        inventory.diagnostics << [
            key: "sensor-${sensorKey}", dni: dni,
            label: "${eqName}: ${diagState.name ?: 'Diagnostic ' + diagId}",
            description: "Equipment diagnostic reading",
            installed: installedDnis.contains(dni), category: "diagnostic"
        ]
    }

    return inventory
}

void createDeviceByKey(String key) {
    String baseDni = device.deviceNetworkId
    String sysName = state.systemName ?: "Lennox"

    if (key.startsWith("zone-")) {
        String zoneId = key.replace("zone-", "")
        Map zoneState = state.zones?.get(zoneId)
        if (zoneState) createOrUpdateZoneThermostat(zoneId.toInteger(), zoneState)
    } else if (key.startsWith("switch-")) {
        String switchType = key.replace("switch-", "")
        Map labelMap = [ventilation: "Ventilation", allergenDefender: "Allergen Defender",
                        manualAway: "Manual Away Mode", smartAwayEnable: "Smart Away",
                        zoning: "Zoning Mode", parameterSafety: "Parameter Safety"]
        String label = labelMap[switchType] ?: switchType
        createSwitchDevice(switchType, label)
    } else if (key.startsWith("sensor-diag-")) {
        String diagPart = key.replace("sensor-diag-", "")
        String[] parts = diagPart.split("-")
        if (parts.length == 2) {
            Integer eqId = parts[0].toInteger()
            Integer diagId = parts[1].toInteger()
            String diagKey = "${eqId}_${diagId}"
            Map diagState = state.equipmentDiagnostics?.get(diagKey)
            if (diagState) updateDiagnosticSensor(eqId, diagId, diagState)
        }
    } else if (key.startsWith("sensor-ble-")) {
        String bleId = key.replace("sensor-ble-", "")
        Map bleState = state.bleDevices?.get(bleId)
        if (bleState) {
            String dni = "${baseDni}-sensor-ble-${bleId}"
            if (!getChildDevice(dni)) {
                String label = bleState.deviceName ?: "BLE Device ${bleId}"
                def child = addChildDevice("rbyrbt", "Lennox iComfort Child Sensor", dni,
                    [name: "Lennox ${label}", label: label, isComponent: false])
                child.updateDataValue("sensorType", "ble")
                child.sendEvent(name: "sensorType", value: "ble")
                child.updateDataValue("bleId", bleId)
            }
        }
    } else if (key.startsWith("sensor-")) {
        String sensorType = key.replace("sensor-", "")
        Map labelMap = [outdoorTemperature: "Outdoor Temperature", wifiRssi: "WiFi Signal",
                        activeAlerts: "Active Alerts", homeState: "Home State",
                        internetStatus: "Internet Status", relayStatus: "Relay Server Status",
                        iaqPm25: "IAQ PM2.5", iaqVoc: "IAQ VOC", iaqCo2: "IAQ CO2"]
        String label = labelMap[sensorType] ?: sensorType
        createSensorDevice(sensorType, label)
    } else {
        log.warn "Unknown device key: ${key}"
    }
    resubscribeForChildDevices()
}

void removeDeviceByKey(String key) {
    String dni = "${device.deviceNetworkId}-${key}"
    def child = getChildDevice(dni)
    if (child) {
        log.info "Removing device: ${child.label} (${dni})"
        deleteChildDevice(dni)
    } else {
        log.warn "Device not found for removal: ${dni}"
    }
    resubscribeForChildDevices()
}

// Commands for publishing to Lennox
void publishMessage(Map data, String additionalParameters = null) {
    if (!state.connected) {
        log.warn "Cannot publish - not connected"
        return
    }
    
    String sysId = state.sysId ?: "LCC"
    state.publishMessageId = (state.publishMessageId ?: 0) + 1
    
    String url
    Map params
    
    Map message = [
        MessageType: "Command",
        SenderID: getClientId(),
        MessageID: String.format("%08d-0000-0000-0000-000000000000", state.publishMessageId),
        TargetID: sysId,
        Data: data
    ]
    
    if (additionalParameters) {
        message.AdditionalParameters = additionalParameters
    }
    
    String body = JsonOutput.toJson(message)
    
    if (logEnable) log.debug "Publishing: ${body}"
    
    if (state.isLocalConnection) {
        // Local connection: one Content-Type only to avoid 400 from header quirks
        url = "https://${settings.ipAddress}/Messages/Publish"
        params = [
            uri: url,
            ignoreSSLIssues: true,
            contentType: "application/json",
            body: body,
            timeout: 30
        ]
    } else {
        // Cloud connection
        url = CLOUD_PUBLISH_URL
        params = [
            uri: url,
            requestContentType: "application/json",
            contentType: "application/json",
            body: body,
            timeout: 30,
            headers: [
                "Authorization": state.loginBearerToken,
                "User-Agent": USER_AGENT,
                "Accept": "*/*"
            ]
        ]
    }
    
    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                def result = resp.data
                if (result instanceof String) {
                    result = new JsonSlurper().parseText(result)
                }
                if (result?.code == 1) {
                    if (logEnable) log.debug "Publish successful"
                    triggerFastPoll()
                } else {
                    log.error "Publish failed with code: ${result?.code}"
                }
            } else {
                String errBody = ""
                try { errBody = resp.getData() ?: "" } catch (Exception ignored) {}
                if (errBody) errBody = " - " + errBody.take(300)
                log.error "Publish failed with status: ${resp.status}${errBody}"
            }
        }
    } catch (Exception e) {
        log.error "Publish exception: ${e.message}"
    }
}

// System commands
void setAllergenDefender(String enabled) {
    Boolean mode = enabled == "true"
    Map data = [system: [config: [allergenDefender: mode]]]
    publishMessage(data)
    state.allergenDefender = mode
    sendEventIfChanged("allergenDefender", mode.toString())
    updateSystemSwitches()
}

void setVentilationMode(String mode) {
    if (!(mode in [LENNOX_VENTILATION_MODE_ON, LENNOX_VENTILATION_MODE_OFF, LENNOX_VENTILATION_MODE_INSTALLER])) {
        log.error "Invalid ventilation mode: ${mode}"
        return
    }
    Map data = [system: [config: [ventilationMode: mode]]]
    publishMessage(data)
    state.ventilationMode = mode
    sendEventIfChanged("ventilationMode", mode)
    updateSystemSwitches()
}

void setManualAwayMode(String enabled) {
    Boolean mode = enabled == "true"
    Map data = [occupancy: [manualAway: mode]]
    publishMessage(data)
    state.manualAwayMode = mode
    sendEventIfChanged("manualAwayMode", mode.toString())
    updateAwayModeSwitches()
}

void setSmartAwayEnabled(String enabled) {
    Boolean mode = enabled == "true"
    Map data = [occupancy: [smartAway: [config: [enabled: mode]]]]
    publishMessage(data)
    state.smartAwayEnabled = mode
    sendEventIfChanged("smartAwayEnabled", mode.toString())
    updateAwayModeSwitches()
}

void cancelSmartAway() {
    Map data = [occupancy: [smartAway: [config: [cancel: true]]]]
    publishMessage(data)
}

void setCirculateTime(BigDecimal percent) {
    Integer pct = percent.intValue()
    if (pct < 15 || pct > 45) {
        log.error "Circulate time must be between 15 and 45 percent"
        return
    }
    Map data = [system: [config: [circulateTime: pct]]]
    publishMessage(data)
}

void setDiagnosticLevel(String level) {
    Integer lvl = level.toInteger()
    if (!(lvl in [0, 1, 2])) {
        log.error "Diagnostic level must be 0, 1, or 2"
        return
    }
    if (lvl == 2) {
        Boolean s30 = isModelS30()

        if (s30) {
            if (!state.isLocalConnection) {
                log.error "Diagnostic level 2 blocked -- S30 controllers cannot handle level 2 diagnostics " +
                    "over cloud connections. The high-frequency data causes the S30 to run out of memory, " +
                    "reboot repeatedly, and lose HVAC control. Use a local LAN connection with internet blocked instead."
                return
            }

            Boolean internetUp = state.internetStatus?.toString() == "true"
            Boolean relayUp = state.relayServerConnected?.toString() == "true"

            if (internetUp || relayUp) {
                List status = []
                if (internetUp) status.add("internet connected")
                if (relayUp) status.add("Lennox relay server connected")
                log.error "Diagnostic level 2 blocked -- S30 controller is currently ${status.join(' and ')}. " +
                    "The S30 pushes diagnostic data every 4 seconds to all connected clients including the Lennox cloud. " +
                    "This can cause the controller to run out of memory, reboot repeatedly, and lose HVAC control. " +
                    "Block the thermostat's internet access at your router, wait for the internetStatus and " +
                    "relayServerConnected attributes to show false, then try again."
                return
            }

            log.warn "Enabling diagnostic level 2 on S30 -- diagnostic data will be sent every 4 seconds. " +
                "Monitor system stability and disable diagnostics (set to 0) if you observe controller reboots."
        } else if (s30 == null) {
            log.warn "Enabling diagnostic level 2 -- controller model not yet identified. " +
                "If this is an S30, level 2 diagnostics can cause instability when the controller has internet access. " +
                "Monitor system stability and disable diagnostics (set to 0) if you observe issues."
        } else {
            log.info "Enabling diagnostic level 2 on ${state.productType} controller."
        }
    }
    Map data = [systemControl: [diagControl: [level: lvl]]]
    publishMessage(data)
}

Boolean isModelS30() {
    if (!state.productType) return null
    return state.productType.toString().toUpperCase().contains("S30")
}

void setCentralMode(String enabled) {
    Boolean mode = (enabled == "true" || enabled == true)
    if (state.numberOfZones == 1) {
        log.error "Central mode is not configurable for single-zone systems"
        return
    }
    Map data = [system: [config: [centralMode: mode]]]
    publishMessage(data)
    state.centralMode = mode
    state.zoningMode = mode ? "central" : "zoned"
    sendEventIfChanged("zoningMode", state.zoningMode)
    updateSystemSwitches()
}

void setTimedVentilation(Integer durationSeconds) {
    if (durationSeconds < 0) {
        log.error "Ventilation duration must be a positive integer"
        return
    }
    if (!state.ventilationUnitType) {
        log.error "System does not support ventilation"
        return
    }
    Map data = [systemController: [command: "ventilateNow ${durationSeconds}"]]
    publishMessage(data)
}

void parameterSafetyStateChanged(Boolean isOn) {
    state.parameterSafetyEnabled = isOn
    if (logEnable) log.debug "Parameter safety state changed: ${isOn}"
}

void setDehumidificationMode(String mode) {
    if (!(mode in ["auto", "medium", "high"])) {
        log.error "Invalid dehumidification mode: ${mode}"
        return
    }
    Map data = [system: [config: [dehumidificationMode: mode]]]
    publishMessage(data)
}

void clearAlert(BigDecimal alertId) {
    Integer id = alertId.intValue()
    Map data = [alerts: [active: [[id: id, alert: [acknowledge: true]]]]]
    publishMessage(data)
    log.info "Cleared alert ${id}"
}

void setEquipmentParameter(BigDecimal equipId, BigDecimal paramId, String value) {
    if (!state.parameterSafetyEnabled) {
        log.error "Parameter safety switch must be enabled before modifying equipment parameters"
        return
    }
    
    Integer eId = equipId.intValue()
    Integer pId = paramId.intValue()
    
    // Attempt to parse value as number if possible
    def parsedValue
    try {
        if (value.contains(".")) {
            parsedValue = value.toBigDecimal()
        } else {
            parsedValue = value.toInteger()
        }
    } catch (Exception e) {
        parsedValue = value
    }
    
    Map data = [
        equipments: [[
            id: eId,
            equipment: [
                parameters: [[
                    id: pId,
                    parameter: [
                        value: parsedValue
                    ]
                ]]
            ]
        ]]
    ]
    
    publishMessage(data)
    log.info "Set equipment ${eId} parameter ${pId} to ${value}"
}

void getEquipmentDiagnostics() {
    log.info "Equipment Diagnostics:"
    state.equipment?.each { equipId, equipState ->
        log.info "  Equipment ${equipId}: ${equipState.equipmentTypeName ?: equipState.equipType}"
        log.info "    Model: ${equipState.unitModelNumber ?: 'N/A'}"
        log.info "    Serial: ${equipState.unitSerialNumber ?: 'N/A'}"
    }
    
    if (state.equipmentDiagnostics) {
        Integer count = state.equipmentDiagnostics.size()
        log.info "Live Diagnostic Readings (${count} sensors):"
        state.equipmentDiagnostics.each { key, diagState ->
            String eqName = state.equipment?.get(diagState.equipmentId?.toString())?.equipmentTypeName ?: "Eq ${diagState.equipmentId}"
            String valueStr = diagState.value != null ? "${diagState.value}" : "N/A"
            if (diagState.unit) valueStr += " ${diagState.unit}"
            String validStr = diagState.valid == false ? " [INVALID]" : ""
            log.info "  [${eqName}] ${diagState.name ?: key}: ${valueStr}${validStr}"
        }
    } else {
        log.info "No diagnostic readings available. Set diagnostic level to 1 or 2 to enable."
    }
}

void refreshToken() {
    if (state.isLocalConnection) {
        log.info "Token refresh not needed for local connections"
        return
    }
    
    log.info "Refreshing cloud authentication token..."
    state.loginBearerToken = null
    state.authBearerToken = null
    authenticateCloud()
}

// Zone commands (called by child devices)
void setZoneHvacMode(Integer zoneId, String mode) {
    Integer scheduleId = 16 + zoneId  // Manual mode schedule
    Map data = [
        schedules: [[
            id: scheduleId,
            schedule: [
                periods: [[
                    id: 0,
                    period: [systemMode: mode]
                ]]
            ]
        ]]
    ]
    publishMessage(data)
}

void setZoneFanMode(Integer zoneId, String mode) {
    Integer scheduleId = 16 + zoneId
    Map data = [
        schedules: [[
            id: scheduleId,
            schedule: [
                periods: [[
                    id: 0,
                    period: [fanMode: mode]
                ]]
            ]
        ]]
    ]
    publishMessage(data)
}

void setZoneSetpoints(Integer zoneId, BigDecimal hsp, BigDecimal csp, BigDecimal sp = null) {
    Integer scheduleId = 16 + zoneId
    Map period = [:]
    
    if (sp != null) {
        // Single setpoint mode
        period.sp = sp.intValue()
        period.spC = fahrenheitToCelsius(sp)
    } else {
        if (hsp != null) {
            period.hsp = hsp.intValue()
            period.hspC = fahrenheitToCelsius(hsp)
        }
        if (csp != null) {
            period.csp = csp.intValue()
            period.cspC = fahrenheitToCelsius(csp)
        }
    }
    
    Map data = [
        schedules: [[
            id: scheduleId,
            schedule: [
                periods: [[
                    id: 0,
                    period: period
                ]]
            ]
        ]]
    ]
    publishMessage(data)
}

void setZoneHumidityMode(Integer zoneId, String mode) {
    Integer scheduleId = 16 + zoneId
    Map data = [
        schedules: [[
            id: scheduleId,
            schedule: [
                periods: [[
                    id: 0,
                    period: [humidityMode: mode]
                ]]
            ]
        ]]
    ]
    publishMessage(data)
}

void setZoneHumiditySetpoints(Integer zoneId, Integer husp, Integer desp) {
    Integer scheduleId = 16 + zoneId
    Map period = [:]
    if (husp != null) period.husp = husp
    if (desp != null) period.desp = desp
    
    Map data = [
        schedules: [[
            id: scheduleId,
            schedule: [
                periods: [[
                    id: 0,
                    period: period
                ]]
            ]
        ]]
    ]
    publishMessage(data)
}

void setZoneSchedule(Integer zoneId, Integer scheduleId) {
    Map data = [
        zones: [[
            id: zoneId,
            config: [scheduleId: scheduleId]
        ]]
    ]
    publishMessage(data)
}

// Poll interval option lists (ordered)
List localPollOptions() {
    return [
        ["1": "1 second"],
        ["2": "2 seconds"],
        ["3": "3 seconds"],
        ["4": "4 seconds"],
        ["5": "5 seconds"],
        ["10": "10 seconds"],
        ["15": "15 seconds"],
        ["20": "20 seconds"],
        ["30": "30 seconds (default)"],
        ["45": "45 seconds"],
        ["60": "1 minute"],
        ["90": "90 seconds"],
        ["120": "2 minutes"],
        ["180": "3 minutes"],
        ["240": "4 minutes"],
        ["300": "5 minutes"],
        ["600": "10 minutes"],
        ["900": "15 minutes"],
        ["1200": "20 minutes"],
        ["1800": "30 minutes"],
        ["2700": "45 minutes"],
        ["3600": "1 hour"]
    ]
}

List cloudPollOptions() {
    return [
        ["10": "10 seconds"],
        ["15": "15 seconds"],
        ["20": "20 seconds"],
        ["30": "30 seconds"],
        ["45": "45 seconds"],
        ["60": "1 minute (default)"],
        ["90": "90 seconds"],
        ["120": "2 minutes"],
        ["180": "3 minutes"],
        ["240": "4 minutes"],
        ["300": "5 minutes"],
        ["600": "10 minutes"],
        ["900": "15 minutes"],
        ["1200": "20 minutes"],
        ["1800": "30 minutes"],
        ["2700": "45 minutes"],
        ["3600": "1 hour"],
        ["5400": "1.5 hours"],
        ["7200": "2 hours"]
    ]
}

// Utility methods
def convertTemperature(tempF, tempC) {
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

String sanitizeAppId(String appId) {
    if (!appId) return null
    return appId.replaceAll('[^a-zA-Z0-9_]', '')
}

void sendEventIfChanged(String name, value, String unit = null, String descriptionText = null) {
    if (device.currentValue(name) != value) {
        Map evt = [name: name, value: value]
        if (unit) evt.unit = unit
        if (descriptionText) evt.descriptionText = descriptionText
        sendEvent(evt)
    }
}

void removeChildDevices() {
    log.info "Removing all child devices"
    getChildDevices().each { child ->
        deleteChildDevice(child.deviceNetworkId)
    }
    state.childDevicesCreated = false
    state.initialChildCreationDone = false
}

void logsOff() {
    log.warn "Debug logging disabled automatically after 24 hours"
    device.updateSetting("logEnable", [type: "bool", value: false])
}
