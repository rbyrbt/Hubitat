# Winix Air Purifiers for Hubitat

Control Winix air purifiers from your Hubitat Elevation hub. This app and drivers connect to the Winix cloud, expose purifiers as Hubitat devices, and support power, fan speed, Plasmawave, air quality monitoring, and optional child devices for automations.

## Features

- **Power Control** - Turn purifiers on/off
- **Fan Speed Control** - Low, Medium, High, Turbo
- **Auto Mode** - Enable/disable automatic air quality mode
- **Sleep Mode** - Enable/disable sleep mode (quiet operation)
- **Plasmawave** - Enable/disable Plasmawave technology
- **Air Quality Monitoring** - View current air quality readings
- **Ambient Light** - Monitor ambient light sensor readings
- **Filter Life Tracking** - Track filter usage hours and replacement status
- **Child Devices** - Optional separate devices for Air Quality Sensor, Plasmawave, Auto Mode, and Sleep Mode switches

## Device Support

- Winix C545
- Winix C610 (untested)
- Winix C909 (untested)
- May support other models

## Installation

### Hubitat Package Manager (Recommended)

1. Open [Hubitat Package Manager](https://hubitatpackagemanager.hubitatcommunity.com/).
2. Search for **Winix Air Purifiers** to install the package.

### Driver and App import from GitHub (Manual Option)

If you prefer not to use HPM, install via Hubitat’s **Import** (drivers first, then app).

**Drivers Code** → **New Driver** → **Import**. Paste each URL, click **Import**, then **Save** (parent first, then child drivers):

- `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/drivers/winix-purifier-driver.groovy`
- `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/drivers/winix-child-switch-driver.groovy`
- `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/drivers/winix-child-air-quality-driver.groovy`
- `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/drivers/winix-child-light-sensor-driver.groovy`

**Apps Code** → **New App** → **Import**. Paste this URL and click **Import**, then **Save**:

- `https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/apps/winix-connect-app.groovy`

### Add the App

1. Go to **Apps** → **Add User App**
2. Select **Winix Connect**
3. Choose an authentication method (see below)
4. Click **Discover & Manage Devices**, then click **Discover Devices** to find your purifiers
5. Toggle devices on to add them (devices show "✓ Added" when added)
6. Configure child device options as desired

## Authentication Methods

### Method 1: In-App Login (Recommended)

1. Enter your Winix account email and password in the app
2. Click **Login** to authenticate
3. Tokens are automatically managed and refreshed

### Method 2: Manual Token Entry (Fallback)

If in-app login doesn't work (for example, due to special characters in your password or regional restrictions), you can obtain Access and Refresh tokens elsewhere and enter them in the app:

- **[homebridge-winix-purifiers](https://github.com/regaw-leinad/homebridge-winix-purifiers)** — The upstream project uses the same Winix Cognito authentication. You can run its login in a Node.js environment and copy the tokens from its config or logs.
- **[winix-api](https://github.com/regaw-leinad/winix-api)** — Reference implementation and API documentation for the Winix cloud API and authentication; useful if you want to script or debug token generation.

Then in the Hubitat app: go to **Manual Token Entry**, paste the Access Token and Refresh Token, and click **Save Tokens**.

### Alternate Winix Account (Recommended for stable use)

Winix limits each account to a single active session. If you use the same account in both the Winix mobile app and this Hubitat integration, one session can log the other out, leading to repeated authentication failures. Using a **dedicated alternate Winix account** for Hubitat avoids this: create a second Winix account, link your purifiers to it (or share access per Winix’s process), and use that account only for Hubitat. Your main account can stay signed in on the Winix app.

Step-by-step instructions for creating and linking an alternate account are in the [homebridge-winix-purifiers](https://github.com/regaw-leinad/homebridge-winix-purifiers) Wiki: [Alternate Winix Account: Creating and Linking](https://github.com/regaw-leinad/homebridge-winix-purifiers/wiki/Alternate-Winix-Account:-Creating-and-Linking). This approach was documented by the upstream project (credit: [regaw-leinad/homebridge-winix-purifiers](https://github.com/regaw-leinad/homebridge-winix-purifiers)).

## Device Commands

### Set Fan Speed (Custom Dropdown)

Clean dropdown with these options:

- **low** - Low speed
- **medium** - Medium speed
- **high** - High speed
- **turbo** - Maximum speed
- **auto** - Automatic mode based on air quality

### Set Auto Mode

- **on** - Enable auto mode (speed adjusts based on air quality)
- **off** - Disable auto mode (manual speed control)

### Set Sleep Mode

- **on** - Enable sleep mode (quiet operation, airflow code 06)
- **off** - Disable sleep mode (returns to low speed)

### Set Plasmawave

- **on** - Enable Plasmawave ionizer
- **off** - Disable Plasmawave ionizer

### Other Commands

- **On/Off** - Power control
- **Refresh** - Manually refresh device status
- **Configure** - Re-initialize device settings

## Winix API Reference

### Attribute Codes

| Code | Attribute     | Values                                         |
| ---- | ------------- | ---------------------------------------------- |
| A02  | Power         | 0=Off, 1=On                                    |
| A03  | Mode          | 01=Auto, 02=Manual                             |
| A04  | Airflow       | 01=Low, 02=Medium, 03=High, 05=Turbo, 06=Sleep |
| A07  | Plasmawave    | 0=Off, 1=On                                    |
| A21  | Filter Hours  | Integer (hours used)                           |
| S07  | Air Quality   | 01=Good, 02=Fair, 03=Poor                      |
| S14  | Ambient Light | Integer (lux)                                  |

### API Endpoints

- **Status**: `GET https://us.api.winix-iot.com/common/event/sttus/devices/{deviceId}`
- **Control**: `GET https://us.api.winix-iot.com/common/control/devices/{deviceId}/A211/{attribute}:{value}`

## Child Devices

The app can create optional child devices for:

- **Air Quality Sensor** - Exposes air quality as a separate sensor (works with HomeKit Bridge)
- **Plasmawave Switch** - Toggle Plasmawave on/off
- **Auto Mode Switch** - Toggle Auto Mode on/off
- **Sleep Mode Switch** - Toggle Sleep Mode on/off

These appear as separate devices and can be used in dashboards, automations, or exposed to HomeKit.

## Troubleshooting

### Login Issues

- Ensure your Winix account credentials are correct
- Try the manual token entry method as a fallback
- Check Hubitat logs for specific error messages

### Device Not Responding

- Click **Refresh** on the device page
- Check that the device is online in the Winix mobile app
- Verify your access token hasn't expired (re-login if needed)

### Fan Speed Dropdown Shows Extra Options

The built-in FanControl capability includes default speeds that can't be removed. Use the custom **Set Fan Speed** dropdown instead, which has only the valid options.

### Commands Not Working

- Enable debug logging in device preferences
- Check logs for "control success" messages
- Verify the attribute codes match your device model

## Donations

If you find this helpful, feel free to drop a tip [https://ko-fi.com/rbyrbt](https://ko-fi.com/rbyrbt)

## Credits

This integration is a Hubitat adaptation of [homebridge-winix-purifiers](https://github.com/regaw-leinad/homebridge-winix-purifiers) by [regaw-leinad](https://github.com/regaw-leinad). API reference: [winix-api](https://github.com/regaw-leinad/winix-api).

## License

Licensed under the MIT License.
