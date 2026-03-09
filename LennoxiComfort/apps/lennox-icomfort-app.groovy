/**
 *  Lennox iComfort Manager
 *
 *  Manages Lennox iComfort S30/E30/S40/M30 thermostats on Hubitat.
 *  Provides a guided setup wizard and multi-system dashboard.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *  Based on Home Assistant integration lennoxs30 by PeteRager (MIT License) https://github.com/PeteRager/lennoxs30
 *
 *  REVISION HISTORY
 *
 *  v1.1.0   03-07-26   Initial release
 *  v1.1.1   03-09-26   RequestData improvements
 *
 */

definition(
    name: "Lennox iComfort Manager",
    namespace: "rbyrbt",
    author: "rbyrbt",
    description: "Setup and manage Lennox iComfort S30/E30/S40/M30 thermostats",
    category: "Climate",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true,
    importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/apps/lennox-icomfort-app.groovy"
)

preferences {
    page(name: "mainPage")
    page(name: "addSystemPage")
    page(name: "credentialsPage")
    page(name: "optionsPage")
    page(name: "manageSystemPage")
    page(name: "manageDevicesPage")
    page(name: "removeSystemPage")
    page(name: "globalSettingsPage")
    page(name: "setupCompletePage")
}

// ---------------------------------------------------------------------------
// Main Dashboard Page
// ---------------------------------------------------------------------------

def mainPage() {
    dynamicPage(name: "mainPage", title: "Manage your Lennox iComfort S30, E30, S40, and M30 thermostats", install: true, uninstall: true) {

        // List configured systems
        def childDevices = getChildDevices()
        if (childDevices?.size() > 0) {
            childDevices.each { dev ->
                try {
                    String connState = dev.currentValue("connectionState") ?: "Unknown"
                    String systemName = dev.currentValue("systemName") ?: dev.label ?: "Lennox System"
                    String outdoorTemp = dev.currentValue("outdoorTemperature") ?: "N/A"
                    String zones = dev.currentValue("numberOfZones") ?: "?"
                    String connType = dev.getDataValue("connectionType") ?: "local"

                    String statusIcon
                    switch (connState) {
                        case "Connected":
                            statusIcon = "&#10003;"
                            break
                        case "Connecting":
                            statusIcon = "&#8634;"
                            break
                        case "Waiting to Retry":
                            statusIcon = "&#8635;"
                            break
                        default:
                            statusIcon = "&#10007;"
                    }

                    String summary = "${statusIcon} <b>${systemName}</b> (${connType})<br/>"
                    summary += "&nbsp;&nbsp;&nbsp;Status: ${connState} | Outdoor: ${outdoorTemp}° | Zones: ${zones}"

                    section(summary) {
                        href "manageSystemPage", title: "System Details",
                             description: "View system info, live data, and actions to reconnect or remove system",
                             params: [deviceId: dev.deviceNetworkId]
                        href "manageDevicesPage", title: "Manage Child Devices",
                             description: "Add or remove sensors, switches, and diagnostic devices",
                             params: [deviceId: dev.deviceNetworkId]
                    }
                } catch (Exception e) {
                    section("&#10007; ${dev.label ?: dev.deviceNetworkId}") {
                        href "manageSystemPage", title: "System Details",
                             description: "Error loading status: ${e.message}",
                             params: [deviceId: dev.deviceNetworkId]
                    }
                }
            }
        } else {
            section("") {
                paragraph "No Lennox systems configured yet. Tap below to add one."
                href "addSystemPage", title: "Add New System", description: "Set up a new Lennox thermostat"
            }
        }

        section("<b>Settings & Setup</b>") {
            href "globalSettingsPage", title: "Global Settings", description: "Default polling and logging options"
            if (childDevices?.size() > 0) {
                href "addSystemPage", title: "Add Another System", description: "Most users only need one"
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add System Wizard - Page 1: Connection Type & Credentials
// ---------------------------------------------------------------------------

def addSystemPage() {
    state.remove("lastCreateResult")
    dynamicPage(name: "addSystemPage", title: "Add Lennox System", nextPage: "credentialsPage", uninstall: false) {
        section("<b>Connection Type</b>") {
            paragraph "Choose how to connect to your Lennox thermostat."
            input name: "newConnectionType", type: "enum", title: "Connection Type",
                  options: ["cloud": "Lennox Cloud", "local": "Local LAN"],
                  required: true, submitOnChange: true
        }

        if (newConnectionType == "cloud") {
            section("<b>Lennox Cloud Credentials</b>") {
                paragraph "Enter your Lennox iComfort account credentials (same as the mobile app). Works with all supported models."
                input name: "newEmail", type: "string", title: "Email Address", required: true
                input name: "newPassword", type: "password", title: "Password", required: true
            }
        } else if (newConnectionType == "local") {
            section("<b>Local Connection</b>") {
                paragraph "Enter the IP address of your Lennox thermostat (S30, S40, E30 only). You can find this in your router's DHCP client list or the thermostat's WiFi settings."
                input name: "newIpAddress", type: "string", title: "Thermostat IP Address",
                      required: true, description: "e.g. 192.168.1.100"
            }
        }

        section("<b>System Label</b>") {
            input name: "newSystemLabel", type: "string", title: "Device Label",
                  description: "e.g. Main Floor Lennox", required: true, defaultValue: "Lennox iComfort"
        }
    }
}

// ---------------------------------------------------------------------------
// Add System Wizard - Page 2: Credentials Validation / Options
// ---------------------------------------------------------------------------

def credentialsPage() {
    dynamicPage(name: "credentialsPage", title: "Connection Options", nextPage: "optionsPage", uninstall: false) {
        // Validate inputs from previous page
        Boolean valid = true
        String errorMsg = ""

        if (newConnectionType == "cloud") {
            if (!newEmail || newEmail.trim() == "") {
                valid = false
                errorMsg = "Email is required for cloud connections."
            }
            if (!newPassword || newPassword.trim() == "") {
                valid = false
                errorMsg += " Password is required for cloud connections."
            }
        } else if (newConnectionType == "local") {
            if (!newIpAddress || newIpAddress.trim() == "") {
                valid = false
                errorMsg = "IP address is required for local connections."
            } else if (!isValidIp(newIpAddress)) {
                valid = false
                errorMsg = "\"${newIpAddress}\" does not look like a valid IP address."
            }
        }

        if (!valid) {
            section("<b style='color:red'>Validation Error</b>") {
                paragraph errorMsg
                paragraph "Please go back and correct the issue."
            }
        } else {
            section("<b>Connection Summary</b>") {
                if (newConnectionType == "cloud") {
                    paragraph "Connection: <b>Lennox Cloud</b><br/>Account: <b>${newEmail}</b>"
                } else {
                    paragraph "Connection: <b>Local LAN</b><br/>IP Address: <b>${newIpAddress}</b>"
                }
                paragraph "Label: <b>${newSystemLabel}</b>"
            }

            section("<b>Advanced Connection Settings</b>") {
                input name: "newAppId", type: "string", title: "Application ID (leave blank for default)",
                      required: false, description: "Optional custom app ID"
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add System Wizard - Page 3: Options
// ---------------------------------------------------------------------------

def optionsPage() {
    Boolean isCloud = (newConnectionType == "cloud")
    String defaultPoll = isCloud ? (state.defaultCloudPollInterval ?: "60") : (state.defaultLocalPollInterval ?: "30")

    Boolean created = (state.lastCreateResult == "success")
    dynamicPage(name: "optionsPage", title: "System Options", nextPage: "setupCompletePage", install: false, uninstall: false) {
        if (created) {
            section("<b style='color:green'>System Created Successfully</b>") {
                paragraph "Your Lennox thermostat device has been created. It will begin connecting and discovering zones automatically."
                paragraph "Click <b>Next</b> to finish setup."
            }
        } else if (state.lastCreateResult) {
            section("<b style='color:red'>Error Creating System</b>") {
                paragraph state.lastCreateResult
            }
            section("") {
                input name: "btnCreateSystem", type: "button", title: "Retry Create System"
            }
        } else {
            section("<b>Polling Configuration</b>") {
                if (isCloud) {
                    paragraph "Cloud connections poll Lennox servers over the internet. A longer interval reduces load on Lennox's API."
                    input name: "newPollInterval", type: "enum", title: "Poll Interval (Cloud)",
                          options: cloudPollOptions(), defaultValue: defaultPoll, required: true
                } else {
                    paragraph "Local connections use long-polling on your LAN. A 1-second interval is efficient and recommended."
                    input name: "newPollInterval", type: "enum", title: "Poll Interval (Local)",
                          options: localPollOptions(), defaultValue: defaultPoll, required: true
                }

                input name: "newFastPollInterval", type: "decimal", title: "Fast Poll Interval (seconds)",
                      description: "Polling rate immediately after sending a command", defaultValue: state.defaultFastPollInterval ?: 0.75, required: true

                input name: "newFastPollCount", type: "number", title: "Fast Poll Count",
                      description: "Number of fast polls after a command", defaultValue: state.defaultFastPollCount ?: 10, required: true

                if (!isCloud) {
                    input name: "newLongPollTimeout", type: "number", title: "Long Poll Timeout (seconds)",
                          description: "How long each Retrieve request waits for data (local only)", defaultValue: state.defaultLongPollTimeout ?: 15, required: true
                }
            }

            section("<b>Device Creation</b>") {
                paragraph "Choose which child devices to create automatically."
                input name: "newCreateSensors", type: "bool", title: "Create Sensor Devices",
                      description: "Outdoor temp, alerts, WiFi signal, IAQ", defaultValue: true
                input name: "newCreateSwitches", type: "bool", title: "Create Switch Devices",
                      description: "Ventilation, away modes, allergen defender", defaultValue: true
                input name: "newCreateDiagnosticSensors", type: "bool", title: "Create Diagnostic Sensor Devices",
                      description: "Equipment diagnostics (requires diagnostic level > 0, can be 40-50 sensors)", defaultValue: false
            }

            section("<b>Logging</b>") {
                input name: "newLogEnable", type: "bool", title: "Enable Debug Logging",
                      defaultValue: defaultLogEnable != false
                input name: "newTxtEnable", type: "bool", title: "Enable Description Text Logging",
                      defaultValue: defaultTxtEnable != false
            }

            section("") {
                paragraph "<hr>"
                paragraph "<b>Ready?</b> Click below to create your Lennox system."
                input name: "btnCreateSystem", type: "button", title: "Create System",
                      description: "Add this Lennox thermostat to Hubitat"
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Setup Complete Page
// ---------------------------------------------------------------------------

def setupCompletePage() {
    Boolean created = (state.lastCreateResult == "success")
    state.remove("lastCreateResult")
    dynamicPage(name: "setupCompletePage", title: created ? "Setup Complete" : "Setup Incomplete", install: true, uninstall: false) {
        if (created) {
            section("") {
                paragraph "&#10003; Your Lennox system is ready. It will begin connecting and discovering zones automatically."
                paragraph "Click <b>Done</b> to return to the dashboard."
            }
        } else {
            section("") {
                paragraph "&#10007; No system was created."
                paragraph "Go back to the previous page (System Options) and tap the <b>Create System</b> button to add your Lennox thermostat."
                paragraph "You can also click <b>Done</b> to return to the dashboard and start over."
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Manage System Page
// ---------------------------------------------------------------------------

def manageSystemPage(params) {
    String deviceId = params?.deviceId ?: state.lastManagedDeviceId
    state.lastManagedDeviceId = deviceId

    def dev = getChildDevice(deviceId)

    String systemName = dev?.currentValue("systemName") ?: dev?.label ?: "Lennox System"

    dynamicPage(name: "manageSystemPage", title: "System Details: ${systemName}", install: false, uninstall: false) {
        if (!dev) {
            section("") {
                paragraph "<b style='color:red'>Device not found.</b>"
            }
        } else {
            String connState = dev.currentValue("connectionState") ?: "Not Connected"
            String serial = dev.currentValue("serialNumber") ?: "N/A"
            String software = dev.currentValue("softwareVersion") ?: "N/A"
            String product = dev.currentValue("productType") ?: "N/A"
            String outdoorTemp = dev.currentValue("outdoorTemperature")
            String zones = dev.currentValue("numberOfZones") ?: "?"
            String wifiRssi = dev.currentValue("wifiRssi")
            String msgCount = dev.currentValue("messageCount") ?: "0"
            String lastMsg = dev.currentValue("lastMessageTime") ?: "Never"

            section("<b>System Information</b>") {
                paragraph "<b>Connection:</b> ${connState}"
                paragraph "<b>Product:</b> ${product}"
                paragraph "<b>Serial:</b> ${serial}"
                paragraph "<b>Software:</b> ${software}"
            }

            section("<b>Live Data</b>") {
                paragraph "<b>Outdoor Temperature:</b> ${outdoorTemp != null ? outdoorTemp + '°' : 'N/A'}"
                paragraph "<b>Zones:</b> ${zones}"
                paragraph "<b>WiFi RSSI:</b> ${wifiRssi != null ? wifiRssi + ' dBm' : 'N/A'}"
                paragraph "<b>Messages Received:</b> ${msgCount}"
                paragraph "<b>Last Message:</b> ${lastMsg}"
            }

            section("<b>Actions</b>") {
                input name: "btnReconnect_${deviceId}", type: "button", title: "Reconnect",
                      description: "Drop the current connection and reconnect to the thermostat"
                input name: "btnRefresh_${deviceId}", type: "button", title: "Refresh Data",
                      description: "Request an immediate data update from the thermostat"
            }

            section("") {
                href "removeSystemPage", title: "Remove This System",
                     description: "Permanently delete this thermostat and all its child devices",
                     params: [deviceId: deviceId]
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Manage Child Devices Page
// After initial system setup, this page is the source of truth for which child
// devices are installed. Options chosen on optionsPage (Device Creation) apply
// only to the initial creation; changes here are not overwritten by those flags.
// ---------------------------------------------------------------------------

def manageDevicesPage(params) {
    String deviceId = params?.deviceId ?: state.lastManagedDeviceId
    state.lastManagedDeviceId = deviceId

    def dev = getChildDevice(deviceId)

    dynamicPage(name: "manageDevicesPage", title: "Manage Child Devices", install: false, uninstall: false) {
        if (!dev) {
            section("") {
                paragraph "<b style='color:red'>Device not found.</b>"
            }
            return
        }

        Map inventory
        try {
            inventory = dev.getChildDeviceInventory()
        } catch (Exception e) {
            section("") {
                paragraph "<b style='color:red'>Unable to load device inventory.</b> The system may still be connecting."
                paragraph "Error: ${e.message}"
            }
            return
        }

        Boolean discoveryComplete = dev.isDiscoveryComplete()
        Boolean hasDevices = inventory.zones || inventory.switches || inventory.sensors || inventory.diagnostics

        if (!hasDevices) {
            String connState = dev.currentValue("connectionState") ?: "Not Connected"
            section("") {
                if (connState == "Connected") {
                    paragraph "No child devices are available yet. The system is connected but still discovering devices. Reload this page in a moment."
                } else {
                    paragraph "No child devices are available yet. The system is still connecting (status: <b>${connState}</b>). " +
                              "Devices will appear here once the thermostat connection is established. This typically takes 30\u201360 seconds after setup."
                }
            }
        } else if (!discoveryComplete) {
            section("") {
                paragraph "<i>&#8634; Device discovery is still in progress.</i>"
                paragraph "Switches and sensors you enabled for auto-creation will appear below once discovery finishes. Reload this page in 30\u201360 seconds to see the correct list and toggle states."
            }
        }

        if (discoveryComplete) {
            if (syncDeviceToggles(dev, inventory, deviceId)) {
                inventory = dev.getChildDeviceInventory()
            }
            renderDeviceSection("Zone Thermostats", inventory.zones, deviceId)
            renderDeviceSection("Switch Devices", inventory.switches, deviceId)
            renderDeviceSection("Sensor Devices", inventory.sensors, deviceId)
            renderDeviceSection("Diagnostic Sensors", inventory.diagnostics, deviceId)
        }
    }
}

private Boolean syncDeviceToggles(dev, Map inventory, String deviceId) {
    Boolean changed = false
    List allDevices = (inventory.zones ?: []) + (inventory.switches ?: []) +
                      (inventory.sensors ?: []) + (inventory.diagnostics ?: [])
    allDevices.each { devInfo ->
        String settingName = "toggleDev_${deviceId}_${devInfo.key}"
        def toggleValue = settings[settingName]
        if (toggleValue == null) return  // only act on explicit user toggles from this page

        Boolean wantInstalled = toggleValue as Boolean
        // Apply only real changes: install when user turned on, remove when user turned off.
        if (wantInstalled && !devInfo.installed) {
            try {
                dev.createDeviceByKey(devInfo.key)
                log.info "Installed child device: ${devInfo.label}"
                changed = true
            } catch (Exception e) {
                log.error "Failed to install ${devInfo.label}: ${e.message}"
            }
        } else if (!wantInstalled && devInfo.installed) {
            try {
                dev.removeDeviceByKey(devInfo.key)
                log.info "Removed child device: ${devInfo.label}"
                changed = true
            } catch (Exception e) {
                log.error "Failed to remove ${devInfo.label}: ${e.message}"
            }
        }
        app.removeSetting(settingName)
    }
    return changed
}

void renderDeviceSection(String title, List devices, String parentDeviceId) {
    if (!devices) return

    List installed = devices.findAll { it.installed }
    // Toggle state comes from actual installed state (driver inventory), not optionsPage flags.
    section("<b>${title}</b> (${installed.size()} of ${devices.size()} installed)") {
        devices.each { devInfo ->
            String settingName = "toggleDev_${parentDeviceId}_${devInfo.key}"
            String displayTitle = devInfo.description ?
                "${devInfo.label}<br><small style='color:gray;font-weight:normal'>${devInfo.description}</small>" :
                devInfo.label
            input name: settingName, type: "bool", title: displayTitle,
                  defaultValue: devInfo.installed, submitOnChange: true
        }
    }
}

// ---------------------------------------------------------------------------
// Remove System Page
// ---------------------------------------------------------------------------

def removeSystemPage(params) {
    String deviceId = params?.deviceId ?: state.lastManagedDeviceId

    dynamicPage(name: "removeSystemPage", title: "Remove System", install: false, uninstall: false) {
        def dev = getChildDevice(deviceId)

        if (!dev) {
            section("") {
                paragraph "<b>Device not found.</b>"
            }
        } else {
            section("<b style='color:red'>Confirm Removal</b>") {
                paragraph "This will disconnect from the Lennox thermostat and remove the controller device along with all its child devices (zones, switches, sensors)."
                paragraph "<b>Device:</b> ${dev.label}"
                input name: "btnConfirmRemove_${deviceId}", type: "button", title: "Yes, Remove System"
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Global Settings Page
// ---------------------------------------------------------------------------

def globalSettingsPage() {
    dynamicPage(name: "globalSettingsPage", title: "Global Settings", install: false, uninstall: false) {
        section("<b>Cloud Polling</b>") {
            paragraph "Applied immediately to all cloud-connected systems."
            input name: "defaultCloudPollInterval", type: "enum", title: "Cloud Poll Interval",
                  options: cloudPollOptions(), defaultValue: "60", required: true, submitOnChange: true
        }

        section("<b>Local Polling</b>") {
            paragraph "Applied immediately to all locally-connected systems."
            input name: "defaultLocalPollInterval", type: "enum", title: "Local Poll Interval",
                  options: localPollOptions(), defaultValue: "30", required: true, submitOnChange: true

            input name: "defaultLongPollTimeout", type: "number", title: "Long Poll Timeout (seconds)",
                  description: "How long each Retrieve request waits for data (local only)",
                  defaultValue: 15, required: true, submitOnChange: true
        }

        section("<b>Fast Polling</b>") {
            paragraph "Applied immediately to all systems."
            input name: "defaultFastPollInterval", type: "decimal", title: "Fast Poll Interval (seconds)",
                  description: "Polling rate immediately after sending a command",
                  defaultValue: 0.75, required: true, submitOnChange: true

            input name: "defaultFastPollCount", type: "number", title: "Fast Poll Count",
                  description: "Number of fast polls after a command",
                  defaultValue: 10, required: true, submitOnChange: true
        }

        section("<b>Logging</b>") {
            paragraph "Applied immediately to all controller devices and their child devices."
            input name: "defaultLogEnable", type: "bool", title: "Enable Debug Logging", defaultValue: true, submitOnChange: true
            input name: "defaultTxtEnable", type: "bool", title: "Enable Description Text Logging", defaultValue: true, submitOnChange: true
        }

        // Persist to state for use as defaults in wizard
        state.defaultLocalPollInterval = defaultLocalPollInterval ?: "30"
        state.defaultCloudPollInterval = defaultCloudPollInterval ?: "60"
        state.defaultFastPollInterval = defaultFastPollInterval ?: 0.75
        state.defaultFastPollCount = defaultFastPollCount ?: 10
        state.defaultLongPollTimeout = defaultLongPollTimeout ?: 15

        pushGlobalSettings()
    }
}

// ---------------------------------------------------------------------------
// Device Creation
// ---------------------------------------------------------------------------

/** Build a stable DNI so the same thermostat (same IP or same cloud account) is not added multiple times. */
private String buildStableDni(String connectionType, String ipAddress, String email) {
    String id = (connectionType == "local")
        ? (ipAddress ? ipAddress.replaceAll("[^0-9A-Za-z]", "") : "")
        : (email ? email.replaceAll("[^0-9A-Za-z@.]", "").toLowerCase() : "")
    String prefix = (connectionType == "local") ? "local" : "cloud"
    return "lennox-icomfort-${prefix}-${id}"
}

String createLennoxDevice() {
    try {
        String dni = buildStableDni(newConnectionType ?: "cloud", newIpAddress ?: "", newEmail ?: "")
        if (!dni || dni == "lennox-icomfort-local-" || dni == "lennox-icomfort-cloud-") {
            return "Error: Missing IP address (local) or email (cloud)."
        }
        if (getChildDevice(dni)) {
            return "This system is already added (same IP or cloud account). Open \"System Details\" for that system, or use \"Remove This System\" first if you want to set it up again."
        }

        String label = newSystemLabel ?: "Lennox iComfort"

        log.info "Creating Lennox controller device: ${label} (${dni})"

        def dev = addChildDevice(
            "rbyrbt",
            "Lennox iComfort Driver",
            dni,
            [
                name: "Lennox iComfort Driver",
                label: label,
                isComponent: false
            ]
        )

        if (!dev) {
            return "Failed to create device - addChildDevice returned null."
        }

        // Store connection type as device data
        dev.updateDataValue("connectionType", newConnectionType ?: "cloud")
        dev.updateDataValue("managedByApp", "true")

        // Push configuration to the driver via settings
        Map config = [
            connectionType: newConnectionType ?: "cloud",
            pollInterval: newPollInterval ?: ((newConnectionType == "cloud") ? "60" : "30"),
            fastPollInterval: newFastPollInterval ?: 0.75,
            fastPollCount: newFastPollCount ?: 10,
            longPollTimeout: newLongPollTimeout ?: 15,
            createSensors: newCreateSensors != false,
            createSwitches: newCreateSwitches != false,
            createDiagnosticSensors: newCreateDiagnosticSensors == true,
            logEnable: newLogEnable != false,
            txtEnable: newTxtEnable != false
        ]

        if (newConnectionType == "local") {
            config.ipAddress = newIpAddress
        } else {
            config.email = newEmail
            config.password = newPassword
        }

        if (newAppId) {
            config.appId = newAppId
        }

        dev.configureFromApp(config)

        // Clear wizard inputs
        app.removeSetting("newConnectionType")
        app.removeSetting("newIpAddress")
        app.removeSetting("newEmail")
        app.removeSetting("newPassword")
        app.removeSetting("newSystemLabel")
        app.removeSetting("newAppId")
        app.removeSetting("newPollInterval")
        app.removeSetting("newFastPollInterval")
        app.removeSetting("newFastPollCount")
        app.removeSetting("newLongPollTimeout")
        app.removeSetting("newCreateSensors")
        app.removeSetting("newCreateSwitches")
        app.removeSetting("newCreateDiagnosticSensors")
        app.removeSetting("newLogEnable")
        app.removeSetting("newTxtEnable")

        return "success"

    } catch (Exception e) {
        log.error "Error creating Lennox device: ${e.message}"
        return "Error: ${e.message}"
    }
}

// ---------------------------------------------------------------------------
// Button Handlers
// ---------------------------------------------------------------------------

void appButtonHandler(String btnName) {
    if (btnName == "btnCreateSystem") {
        state.lastCreateResult = createLennoxDevice()
    } else if (btnName.startsWith("btnReconnect_")) {
        String deviceId = btnName.replace("btnReconnect_", "")
        def dev = getChildDevice(deviceId)
        if (dev) {
            log.info "Reconnecting ${dev.label}..."
            dev.connect()
        }
    } else if (btnName.startsWith("btnRefresh_")) {
        String deviceId = btnName.replace("btnRefresh_", "")
        def dev = getChildDevice(deviceId)
        if (dev) {
            log.info "Refreshing ${dev.label}..."
            dev.refresh()
        }
    } else if (btnName.startsWith("btnConfirmRemove_")) {
        String deviceId = btnName.replace("btnConfirmRemove_", "")
        removeSystem(deviceId)
    }
}


// ---------------------------------------------------------------------------
// System Removal
// ---------------------------------------------------------------------------

void removeSystem(String deviceId) {
    def dev = getChildDevice(deviceId)
    if (!dev) {
        log.warn "Device ${deviceId} not found for removal"
        return
    }

    log.info "Removing Lennox system: ${dev.label}"

    try {
        // Disconnect first
        dev.disconnect()
    } catch (Exception e) {
        log.warn "Error disconnecting during removal: ${e.message}"
    }

    try {
        // Remove all grandchildren (zones, switches, sensors) via the driver
        dev.removeChildDevices()
    } catch (Exception e) {
        log.warn "Error removing child devices: ${e.message}"
    }

    try {
        // Remove the controller device itself
        deleteChildDevice(deviceId)
        log.info "Lennox system removed successfully"
    } catch (Exception e) {
        log.error "Error removing device: ${e.message}"
    }
}

// ---------------------------------------------------------------------------
// App Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    log.info "Lennox iComfort Manager installed"
    initialize()
}

def updated() {
    log.info "Lennox iComfort Manager updated"
    initialize()
}

def initialize() {
    log.info "Lennox iComfort Manager initialized with ${getChildDevices()?.size() ?: 0} system(s)"
}

def uninstalled() {
    log.info "Lennox iComfort Manager uninstalling - removing all systems..."
    getChildDevices().each { dev ->
        try {
            dev.disconnect()
            dev.removeChildDevices()
        } catch (Exception e) {
            log.warn "Error during uninstall cleanup: ${e.message}"
        }
        deleteChildDevice(dev.deviceNetworkId)
    }
}

// ---------------------------------------------------------------------------
// Global Settings Push
// ---------------------------------------------------------------------------

void pushGlobalSettings() {
    def devices = getChildDevices()
    if (!devices) return

    devices.each { dev ->
        try {
            String connType = dev.getDataValue("connectionType") ?: "local"
            Map config = [
                logEnable: defaultLogEnable != false,
                txtEnable: defaultTxtEnable != false,
                fastPollInterval: defaultFastPollInterval ?: 0.75,
                fastPollCount: defaultFastPollCount ?: 10,
            ]

            if (connType == "cloud") {
                config.pollInterval = defaultCloudPollInterval ?: "60"
            } else {
                config.pollInterval = defaultLocalPollInterval ?: "30"
                config.longPollTimeout = defaultLongPollTimeout ?: 15
            }

            dev.updateGlobalSettings(config)
        } catch (Exception e) {
            log.warn "Failed to push global settings to ${dev.label}: ${e.message}"
        }
    }
}

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

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

Boolean isValidIp(String ip) {
    if (!ip) return false
    def parts = ip.split("\\.")
    if (parts.size() != 4) return false
    return parts.every { part ->
        try {
            int val = part.toInteger()
            return val >= 0 && val <= 255
        } catch (Exception e) {
            return false
        }
    }
}
