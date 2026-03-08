# Lennox iComfort for Hubitat

Hubitat app and drivers for Lennox iComfort S30, E30, S40, and M30 smart thermostats. Includes a manager app with a guided setup wizard and multi-system dashboard, plus parent/child drivers for full thermostat control.

## Features

- **Manager App**: Guided setup wizard, multi-system dashboard, centralized configuration
- **Full Thermostat Control**: Temperature, mode, fan, and schedule management
- **Multi-Zone Support**: Automatically creates child devices for each zone
- **Humidity Control**: Humidification and dehumidification setpoints
- **Ventilation Control**: On/off and timed ventilation modes
- **Away Mode**: Manual and Smart Away mode control
- **Sensors**: Outdoor temperature, WiFi signal, alerts, IAQ (if equipped)
- **BLE Devices**: Support for S40 BLE temperature/humidity sensors
- **Equipment Diagnostics**: View equipment information and modify parameters
- **Cloud & Local**: Connect via Lennox cloud or local LAN

## Installation

**Repository:** [github.com/rbyrbt/Hubitat](https://github.com/rbyrbt/Hubitat) (package in `LennoxiComfort/`)

### Option A: Import from GitHub (recommended)

Use Hubitat’s **Import** to pull code directly from GitHub. Install drivers first, then the app.

**Drivers (install in this order):**

1. **Drivers Code** > **New Driver** > **Import**. Paste each URL and click **Import**, then **Save**:
   - Parent driver: `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-driver.groovy`
   - Zone thermostat: `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-child-zone-thermostat.groovy`
   - Switch: `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-child-switch.groovy`
   - Switch (Parameter Safety): `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-child-switch-parameter-safety.groovy`
   - Sensor: `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/drivers/lennox-icomfort-child-sensor.groovy`

2. **Apps Code** > **New App** > **Import**. Paste and import, then **Save**:
   - `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/LennoxiComfort/apps/lennox-icomfort-app.groovy`

**Hubitat Package Manager (HPM):** If you use [Hubitat Package Manager](https://hubitatpackagemanager.hubitatcommunity.com/) add the repo `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/repository.json` in Settings, then install “Lennox iComfort” from the package manager.
### Option B: Manual copy-paste

1. **Drivers Code** > **New Driver** for each file in `LennoxiComfort/drivers/` (parent driver first, then all child drivers). Copy the file contents from the [repo](https://github.com/rbyrbt/Hubitat) and paste into each new driver; **Save**.
2. **Apps Code** > **New App**. Copy the contents of `LennoxiComfort/apps/lennox-icomfort-app.groovy` from the repo; **Save**.

### Launch the App

1. Go to **Apps** > **Add User App**
2. Select **Lennox iComfort Manager**
3. The app dashboard will open

### Add a System

1. From the dashboard, tap **Add New System**
2. Select your connection type:
  - **Lennox Cloud**: Enter your iComfort account email and password (works with all models)
  - **Local LAN**: Enter your thermostat's IP address (S30, S40, E30 only)
3. Give the system a label (e.g., "Main Floor Lennox")
4. Configure polling and device creation options
5. Review and confirm

The app will create a controller device and automatically connect. Zone thermostats, switches, and sensors will be created as the system reports its configuration.

### Adding Multiple Systems

Repeat **Add a System** for each additional thermostat. The dashboard will show all configured systems with live status.

## Configuration Options


| Setting                    | Description                                                        | Default      |
| -------------------------- | ------------------------------------------------------------------ | ------------ |
| Connection Type            | Lennox Cloud or Local LAN                                          | -            |
| Email                      | Lennox account email (cloud)                                       | -            |
| Password                   | Lennox account password (cloud)                                    | -            |
| IP Address                 | Thermostat IP (local)                                              | -            |
| Application ID             | Custom app ID for the Lennox session ([details](#application-id))  | auto         |
| Poll Interval (Cloud)      | How often to poll (10s - 2hr)                                      | 1 minute     |
| Poll Interval (Local)      | How often to poll (1s - 1hr)                                       | 30 seconds   |
| Fast Poll Interval         | Polling rate after commands                                        | 0.75 seconds |
| Fast Poll Count            | Number of fast polls after command                                 | 10           |
| Long Poll Timeout          | Retrieve request wait time (local only)                            | 15 seconds   |
| Create Sensors             | Automatically create sensor devices                                | true         |
| Create Switches            | Automatically create switch devices                                | true         |
| Create Diagnostic Sensors  | Create child sensors for equipment diagnostics (can be 40-50+)     | false        |


> **Polling note**: Cloud connections make full HTTPS round-trips to Lennox servers; default is 1 minute. Local connections use long-polling where the thermostat pushes data as it arrives; default is 30 seconds. Faster polling is used automatically during initial device discovery.

> **Application ID**: Each client connected to a Lennox thermostat uses an application ID to identify its session. Leave this blank to use the built-in defaults (`hubitat` for local, a Lennox mobile app ID for cloud). You only need to set a custom value if you are running multiple integrations against the same thermostat simultaneously and want to avoid session conflicts. The app wizard exposes this under **Advanced Connection Settings** on the connection options page.

## Available Commands

### Parent Device (Controller)


| Command                 | Description                                                             |
| ----------------------- | ----------------------------------------------------------------------- |
| connect                 | Connect to thermostat                                                   |
| disconnect              | Disconnect from thermostat                                              |
| refresh                 | Request fresh data                                                      |
| setAllergenDefender     | Enable/disable allergen defender                                        |
| setVentilationMode      | Set ventilation to on/off/installer                                     |
| setManualAwayMode       | Enable/disable manual away mode                                         |
| setSmartAwayEnabled     | Enable/disable smart away                                               |
| cancelSmartAway         | Cancel active smart away                                                |
| setCirculateTime        | Set fan circulate time (15-45%)                                         |
| setDiagnosticLevel      | Set diagnostic level (0/1/2) -- see [Diagnostic Mode](#diagnostic-mode) |
| setDehumidificationMode | Set dehumidification mode                                               |
| setCentralMode          | Enable/disable central mode (zoning)                                    |
| setTimedVentilation     | Run ventilation for specified seconds                                   |
| clearAlert              | Clear an alert by ID                                                    |
| setEquipmentParameter   | Modify equipment parameter (requires Parameter Safety switch on; that switch has its own driver with auto-off preferences) |
| getEquipmentDiagnostics | Log equipment information                                               |
| refreshToken            | Refresh cloud authentication token                                      |
| removeChildDevices      | Remove all child devices                                                |


### Zone Thermostat

Standard Hubitat thermostat commands plus:


| Command               | Description                                      |
| --------------------- | ------------------------------------------------ |
| setHumidityMode       | Set humidity mode (off/humidify/dehumidify/both) |
| setHumidifySetpoint   | Set humidification target                        |
| setDehumidifySetpoint | Set dehumidification target                      |
| setSchedule           | Set schedule by ID                               |
| resumeSchedule        | Resume automatic schedule                        |


## Diagnostic Mode

Lennox controllers support three diagnostic levels, controlled via the `setDiagnosticLevel` command:


| Level | Description                                                                                                                                                                                                                                                                                              |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **0** | Diagnostics disabled (default). The controller sends only normal thermostat data. This is the standard operating mode.                                                                                                                                                                                   |
| **1** | Accepted by the Lennox API but its behavior is undocumented. The upstream lennoxs30 project considers only 0 and 2 as valid levels. May enable a reduced set of diagnostic data or function as a transitional state.                                                                                     |
| **2** | Full diagnostics. Enables all equipment sensor data (voltages, currents, fan speeds, refrigerant pressures) and inverter power monitoring. The controller sends updates every 4 seconds. **S30 controllers require internet isolation -- see warning below.** A power outage resets the level back to 0. |


### Level 2 S30 Stability Warning

> **Warning (S30 only)**: Diagnostic level 2 can destabilize the **S30** controller if it still has internet access. The high-frequency data is pushed to **all connected clients**, including the Lennox cloud and phone apps. This can overwhelm the S30's outbound connection, causing it to run out of memory, reboot repeatedly, and **lose HVAC control**. This issue is well-documented in the upstream [lennoxs30](https://github.com/PeteRager/lennoxs30) project. The E30, S40, and M30 are **not** known to be affected.

The driver uses the `productType` reported by the thermostat to detect S30 controllers and enforces the following safeguards **only for the S30**:

- **S30 on cloud connection**: Level 2 is blocked entirely.
- **S30 on local connection with internet access**: Level 2 is blocked if the thermostat reports that it is still connected to the internet or the Lennox relay server. The driver checks the `internetStatus` and `relayServerConnected` attributes reported by the thermostat itself.
- **S30 on local connection with internet blocked**: Level 2 is allowed. A warning is logged advising you to monitor stability.
- **E30, S40, M30**: Level 2 is allowed without restriction.
- **Model not yet identified**: Level 2 is allowed with a cautionary warning in case the controller turns out to be an S30.

### Using Diagnostic Level 2 Safely on an S30

1. Block the thermostat's internet access at your router (block DNS and all outbound traffic)
2. Wait for the `internetStatus` and `relayServerConnected` attributes on the controller device to show `false`
3. Run `setDiagnosticLevel` with value `2`
4. Monitor system stability -- if you observe controller reboots, set back to `0` immediately

Note: a power outage resets the diagnostic level to 0. If you need diagnostics to persist, you will need an automation to re-enable it after the controller comes back online.

## Troubleshooting

### Connection Issues

**Cloud connection fails:**

- Verify email and password are correct
- Passwords containing `&` or `^` are known to cause issues with the Lennox API
- Try logging into the Lennox iComfort app to confirm credentials
- Check Hubitat logs for specific error messages

**Local connection fails:**

- Verify the IP address is correct and the thermostat has a static IP or DHCP reservation
- Ensure Hubitat and thermostat are on the same network
- Check that port 443 is not blocked
- S40 requires firmware >= 04.25.0070

### No Child Devices Created

- Wait 30-60 seconds after initial connection
- Check that "Create Sensors" and "Create Switches" are enabled in the app or driver preferences
- Check Hubitat logs for errors during device creation

### Thermostat Not Responding

- Check the connectionState attribute on the controller device
- If "Waiting to Retry", the driver will automatically reconnect
- Use the Reconnect button in the app dashboard, or run the disconnect/connect commands manually
- Check for active alerts on the thermostat

### Data Not Updating

- Verify Poll Interval is set appropriately
- Check if the message pump is running (look for messageCount increasing)
- Try the refresh command or the Refresh button in the app

## Technical Details

### Protocol

This integration uses the same HTTPS JSON protocol as the official Lennox iComfort app:

- **Cloud**: HTTPS to Lennox cloud servers with certificate authentication
- **Local**: Direct HTTPS to thermostat (port 443, self-signed certificate)

### Message Flow

1. **Connect**: Establish session with cloud or thermostat
2. **Subscribe**: Request initial data for all system paths
3. **Poll**: Short-poll (cloud) or long-poll for updates (local)
4. **Publish**: Send commands as JSON messages

### Child Device Types


| Driver                                          | Purpose                                                                 |
| ----------------------------------------------- | ----------------------------------------------------------------------- |
| Lennox iComfort Child Zone Thermostat            | Zone climate control                                                    |
| Lennox iComfort Child Switch                     | Binary on/off (ventilation, away modes, allergen defender, zoning)      |
| Lennox iComfort Child Switch - Parameter Safety  | Safety lock for equipment parameter changes; has auto-off timeout prefs |
| Lennox iComfort Child Sensor                     | Temperature, humidity, status sensors                                  |


## Credits

This integration is a Hubitat adaptation of the Home Assistant [lennoxs30](https://github.com/PeteRager/lennoxs30) custom component and [lennoxs30api](https://github.com/PeteRager/lennoxs30api) Python library by [PeteRager](https://github.com/PeteRager).

## License

Licensed under the MIT License.