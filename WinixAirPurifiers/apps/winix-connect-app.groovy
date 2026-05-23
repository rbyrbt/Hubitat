/**
 *  Winix Air Purifier Connect
 *
 *  A Hubitat app to connect and control Winix air purifiers.
 *
 *  Copyright 2026 rbyrbt
 *  If you find this helpful, feel free to drop a tip https://ko-fi.com/rbyrbt
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY WARRANTY. THE AUTHORS ARE NOT LIABLE FOR ANY DAMAGES ARISING FROM ITS USE.
 *  Based on Homebridge integration homebridge-winix-purifiers by regaw-leinad https://github.com/regaw-leinad/homebridge-winix-purifiers
 *
 *  REVISION HISTORY
 *
 *  v1.1.1 05-23-26   Auth fix (winix-api 2.0 / Homebridge 2.2.6); UI/scheduling polish; identity pool session
 *  v1.0   03-07-26   Initial release
 *
 */

import groovy.transform.Field
import java.security.MessageDigest
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

definition(
    name: "Winix Air Purifier Connect",
    namespace: "rbyrbt",
    author: "rbyrbt",
    description: "Connect and control Winix air purifiers",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true,
    importUrl: "https://raw.githubusercontent.com/rbyrbt/Hubitat/main/WinixAirPurifiers/apps/winix-connect-app.groovy"
)

preferences {
    page(name: "mainPage")
    page(name: "loginPage")
    page(name: "tokenEntryPage")
    page(name: "devicesPage")
}

// ==================== Cognito Constants ====================
// From winix-api 2.0.2 / homebridge-winix-purifiers 2.2.6 (Winix Smart v1.5.7 APK).
// Winix rotated the Cognito app client on 2026-04-16 to a public client (no secret).
@Field static final String COGNITO_REGION = "us-east-1"
@Field static final String COGNITO_USER_POOL_ID = "us-east-1_Ofd50EosD"
@Field static final String COGNITO_CLIENT_ID = "5rjk59c5tt7k9g8gpj0vd2qfg9"
@Field static final String COGNITO_IDENTITY_POOL_ID = "us-east-1:84008e15-d6af-4698-8646-66d05c1abe8b"
@Field static final String COGNITO_LOGINS_PROVIDER = "cognito-idp.us-east-1.amazonaws.com/us-east-1_Ofd50EosD"
@Field static final String COGNITO_IDENTITY_URL = "https://cognito-identity.us-east-1.amazonaws.com/"

// Winix mobile API (encrypted payloads as of Winix Smart v1.5.7)
@Field static final String MOBILE_APP_VERSION = "1.5.7"
@Field static final String MOBILE_MODEL = "SM-G988B"
@Field static final String MOBILE_AES_KEY_HEX = "84be38f854e320dd4a0a8c7fe0f3a9b84c288445916933fc222465bbd5a518d0"
@Field static final String MOBILE_AES_IV_HEX = "dfd55f316e72e97b905f8739005c99a7"

// SRP Constants - N is the large safe prime for SRP
@Field static final String N_HEX = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
    "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
    "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
    "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
    "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
    "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
    "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
    "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
    "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
    "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
    "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64" +
    "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
    "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B" +
    "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
    "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
    "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF"
@Field static final String G_HEX = "2"
@Field static final byte[] INFO_BITS = "Caldera Derived Key".getBytes("UTF-8")

// Winix API endpoints
@Field static final String WINIX_API_BASE = "https://us.api.winix-iot.com"
@Field static final String MOBILE_API_BASE = "https://us.mobile.winix-iot.com"
@Field static final String COGNITO_IDP_URL = "https://cognito-idp.us-east-1.amazonaws.com/"

// ==================== App Pages ====================

def mainPage() {
    dynamicPage(name: "mainPage", title: "Manage Your Winix Air Purifiers", install: true, uninstall: true) {
        // Section 1: Authentication
        section("Authentication") {
            if (state.accessToken) {
                def accountLabel = state.username ? " (${maskEmail(state.username)})" : ""
                paragraph "✓ Connected to Winix${accountLabel}"
                href "loginPage", title: "Re-authenticate", description: "Log in with username/password"
                href "tokenEntryPage", title: "Manual Token Entry", description: "Enter tokens manually"
            } else {
                paragraph "Please log in to your Winix account to get started."
                href "loginPage", title: "Log In", description: "Connect your Winix account"
                href "tokenEntryPage", title: "Manual Token Entry", description: "Enter tokens manually if login fails"
            }
            paragraph "<small>For best stability, use a Winix account dedicated to Hubitat (not shared with the mobile app).</small>"
        }
        
        // Section 2: Device Discovery & Management
        if (state.accessToken) {
            section("Devices") {
                href "devicesPage", title: "Discover & Manage Devices", description: "Find and add Winix air purifiers"
                def children = getChildDevices()
                if (children) {
                    paragraph "<b>Managed Devices:</b>"
                    children.each { child ->
                        paragraph "• ${child.label}"
                    }
                } else {
                    paragraph "No devices installed yet."
                }
            }
            section("Child Devices", hideable: true, hidden: false) {
                paragraph "<div style='display: grid; grid-template-columns: 1fr 1fr; gap: 0;'>"
                input name: "exposeAirQuality", type: "bool", title: "Air Quality Sensor", defaultValue: true, width: 6
                input name: "exposeAmbientLight", type: "bool", title: "Ambient Light Sensor", defaultValue: true, width: 6
                input name: "exposePlasmawave", type: "bool", title: "Plasmawave Switch", defaultValue: true, width: 6
                input name: "exposeAutoSwitch", type: "bool", title: "Auto Mode Switch", defaultValue: true, width: 6
                input name: "exposeSleepSwitch", type: "bool", title: "Sleep Mode Switch", defaultValue: true, width: 6
            }
        }
        
        // Section 3: Options (last)
        if (state.accessToken) {
            section("Polling Intervals") {
                input name: "deviceRefreshIntervalMinutes", type: "number", title: "Device Refresh Interval (minutes)",
                      defaultValue: 60, range: "1..1440", description: "How often all purifiers poll status (60 = hourly)"
            }
            section("Filter Alert") {
                input name: "filterReplacementThreshold", type: "number", title: "Filter Replacement Alert (%)", 
                      defaultValue: 10, range: "1..100"
            }
            section("Debug") {
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
            }
        }
    }
}

def loginPage() {
    dynamicPage(name: "loginPage", title: "Winix Account Login") {
        section {
            paragraph "Enter your Winix account credentials. These are the same credentials you use in the Winix app."
            input name: "winixUsername", type: "email", title: "Email", required: true, submitOnChange: true
            input name: "winixPassword", type: "password", title: "Password", required: true, submitOnChange: true
        }
        section {
            input name: "doLogin", type: "button", title: "Log In"
        }
        if (state.loginError) {
            section {
                paragraph "<span style='color:red'>${state.loginError}</span>"
            }
            state.loginError = null
        }
        if (state.loginSuccess) {
            section {
                paragraph "<span style='color:green'>Successfully logged in!</span>"
                href "mainPage", title: "Back to main page", description: "Discover and add purifiers"
            }
            state.loginSuccess = null
        }
        section("Having trouble?") {
            href "tokenEntryPage", title: "Manual Token Entry", description: "Use if automatic login doesn't work"
        }
    }
}

def tokenEntryPage() {
    dynamicPage(name: "tokenEntryPage", title: "Manual Token Entry") {
        section {
            paragraph "If automatic login isn't working, obtain tokens from the upstream Homebridge plugin or winix-api (same Cognito account)."
            paragraph "<b>Instructions:</b><br>1. Use <a href='https://github.com/regaw-leinad/homebridge-winix-purifiers'>homebridge-winix-purifiers</a> or <a href='https://github.com/regaw-leinad/winix-api'>winix-api</a> to log in<br>2. Copy the access and refresh tokens<br>3. Paste them below"
        }
        section("Tokens") {
            input name: "manualAccessToken", type: "textarea", title: "Access Token", required: false, submitOnChange: true
            input name: "manualRefreshToken", type: "textarea", title: "Refresh Token", required: false, submitOnChange: true
        }
        section {
            input name: "doTestToken", type: "button", title: "Test Token"
            input name: "doSaveTokens", type: "button", title: "Save Tokens"
        }
        if (state.tokenTestResult) {
            section {
                paragraph state.tokenTestResult
            }
            state.tokenTestResult = null
        }
        if (state.tokenError) {
            section {
                paragraph "<span style='color:red'>${state.tokenError}</span>"
            }
            state.tokenError = null
        }
        if (state.tokenSuccess) {
            section {
                paragraph "<span style='color:green'>Tokens saved successfully!</span>"
                href "mainPage", title: "Back to main page", description: "Discover and add purifiers"
            }
            state.tokenSuccess = null
        }
    }
}

def devicesPage() {
    // Sync device toggles on page load
    syncDeviceToggles()
    
    dynamicPage(name: "devicesPage", title: "Manage Devices") {
        section {
            input name: "doDiscover", type: "button", title: "Discover Devices"
        }
        if (state.discoverMessage) {
            section {
                paragraph state.discoverMessage
            }
            state.discoverMessage = null
        }
        if (state.discoveredDevices) {
            section("Devices") {
                paragraph "Toggle devices to add or remove them"
                state.discoveredDevices.each { device ->
                    def installed = getChildDevice("winix_${device.deviceId}")
                    def addedIndicator = installed ? " (✓ Added)" : ""
                    input name: "device_${device.deviceId}", type: "bool", 
                          title: "${device.deviceAlias}${addedIndicator}", description: device.modelName,
                          defaultValue: installed != null, submitOnChange: true
                }
            }
        } else if (!state.discoverMessage) {
            section {
                paragraph "Click <b>Discover Devices</b> to find purifiers on your account."
            }
        }
    }
}

def syncDeviceToggles() {
    // Sync toggle states with actual installed devices
    if (!state.discoveredDevices) return
    
    state.discoveredDevices.each { device ->
        def installed = getChildDevice("winix_${device.deviceId}")
        def settingName = "device_${device.deviceId}"
        def toggleState = settings[settingName]
        
        if (toggleState == true && !installed) {
            // Toggle is ON but device not installed - install it
            installDevice(device)
        } else if (toggleState == false && installed) {
            // Toggle is OFF but device is installed - uninstall it
            uninstallDevice(device.deviceId)
        }
    }
}

def installDevice(device) {
    def dni = "winix_${device.deviceId}"
    def existing = getChildDevice(dni)
    
    if (!existing) {
        logDebug "Creating device: ${device.deviceAlias}"
        try {
            def child = addChildDevice(
                "rbyrbt",
                "Winix Air Purifier",
                dni,
                [
                    label: device.deviceAlias,
                    name: "Winix ${device.modelName}"
                ]
            )
            
            // Set device data
            child.updateDataValue("deviceId", device.deviceId)
            child.updateDataValue("model", device.modelName)
            child.updateDataValue("firmware", device.mcuVer ?: "Unknown")
            child.updateDataValue("manufacturer", "Winix")
            
            log.info "Device installed: ${device.deviceAlias}"
        } catch (Exception e) {
            log.error "Failed to create device ${device.deviceAlias}: ${e.message}"
        }
    }
}

def uninstallDevice(String deviceId) {
    def dni = "winix_${deviceId}"
    def existing = getChildDevice(dni)
    
    if (existing) {
        logDebug "Removing device: ${existing.label}"
        try {
            deleteChildDevice(dni)
            log.info "Device uninstalled: ${existing.label}"
        } catch (Exception e) {
            log.error "Failed to remove device: ${e.message}"
        }
    }
}


// ==================== Button Handlers ====================

def appButtonHandler(btn) {
    switch(btn) {
        case "doLogin":
            performLogin()
            break
        case "doTestToken":
            testManualToken()
            break
        case "doSaveTokens":
            saveManualTokens()
            break
        case "doDiscover":
            discoverDevices()
            break
    }
}

// ==================== Lifecycle ====================

def installed() {
    logDebug "Installed"
    initialize()
}

def updated() {
    logDebug "Updated"
    initialize()
}

def initialize() {
    logDebug "Initializing"
    unschedule()
    
    if (state.accessToken && state.refreshToken) {
        // Recover session after upgrade or hub reboot (identityId required for device control)
        if (!state.idToken || (state.tokenExpiry && now() > state.tokenExpiry - 60000)) {
            refreshAuthToken()
        } else if (!state.identityId) {
            establishWinixSession()
        }

        runEvery1Hour(refreshAuthToken)
        try {
            scheduleDeviceRefresh()
        } catch (Exception e) {
            log.error "Device refresh schedule failed: ${e.message}; using hourly fallback"
            runEvery1Hour(refreshAllDevices)
        }
    }
}

def scheduleDeviceRefresh() {
    int refreshMinutes = (deviceRefreshIntervalMinutes ?: 60) as int
    refreshMinutes = Math.max(1, Math.min(refreshMinutes, 1440))

    // Cron minutes are 0-59; */60 is invalid. Use hour-based cron when interval >= 60 min.
    if (refreshMinutes >= 1440) {
        schedule("0 0 0 * ? *", refreshAllDevices)
    } else if (refreshMinutes >= 60) {
        int hours = (int) (refreshMinutes / 60)
        if (hours < 1) hours = 1
        if (hours == 1) {
            runEvery1Hour(refreshAllDevices)
        } else if (hours <= 23) {
            schedule("0 0 */${hours} * ? *", refreshAllDevices)
        } else {
            runEvery1Hour(refreshAllDevices)
        }
    } else if (refreshMinutes == 30) {
        runEvery30Minutes(refreshAllDevices)
    } else if (refreshMinutes == 15) {
        schedule("0 */15 * ? * *", refreshAllDevices)
    } else if (refreshMinutes == 10) {
        runEvery10Minutes(refreshAllDevices)
    } else if (refreshMinutes == 5) {
        runEvery5Minutes(refreshAllDevices)
    } else if (refreshMinutes == 1) {
        runEvery1Minute(refreshAllDevices)
    } else if (refreshMinutes > 0 && refreshMinutes < 60) {
        schedule("0 */${refreshMinutes} * ? * *", refreshAllDevices)
    } else {
        runEvery1Hour(refreshAllDevices)
    }
}

def uninstalled() {
    logDebug "Uninstalled"
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

// ==================== Authentication ====================

def performLogin() {
    String username = settings.winixUsername?.trim()
    String password = settings.winixPassword
    
    logDebug "Performing login for ${username}"
    state.loginError = null
    state.loginSuccess = null
    
    if (!username || !password) {
        state.loginError = "Please enter both email and password"
        log.error "Login failed: missing credentials"
        return
    }
    
    try {
        def authResult = authenticateWithSRP(username, password, 3)
        
        if (authResult.success) {
            state.username = username
            state.accessToken = authResult.accessToken
            state.idToken = authResult.idToken
            state.refreshToken = authResult.refreshToken
            state.userId = authResult.userId
            state.tokenExpiry = now() + (authResult.expiresIn * 1000)
            if (!establishWinixSession()) {
                state.loginError = "Login succeeded but Winix session setup failed. Try again or use manual token entry."
                log.error state.loginError
                return
            }
            state.loginSuccess = true
            try {
                initialize()
            } catch (Exception scheduleEx) {
                log.error "Login OK but scheduling failed: ${scheduleEx.message}"
            }
            log.info "Winix login successful for ${maskEmail(username)}"
        } else {
            state.loginError = userFriendlyError(authResult.error ?: "Login failed")
            log.error "Login failed: ${state.loginError}"
        }
    } catch (Exception e) {
        state.loginError = userFriendlyError(e.message)
        log.error "Login exception: ${e.message}"
    }
}

def testManualToken() {
    logDebug "Testing manual token"
    state.tokenTestResult = null
    
    String accessToken = trimToken(settings.manualAccessToken)
    
    if (!accessToken) {
        state.tokenTestResult = "<span style='color:red'>Please enter an Access Token to test</span>"
        return
    }
    
    try {
        // Step 1: Try to decode the JWT
        String userId = decodeJwtSub(accessToken)
        if (!userId) {
            state.tokenTestResult = "<span style='color:red'>✗ Invalid token format - could not decode JWT</span>"
            return
        }
        
        // Step 2: If refresh token provided, verify it can obtain fresh tokens
        String refreshToken = trimToken(settings.manualRefreshToken)
        if (refreshToken) {
            def refreshResult = refreshCognitoToken(refreshToken, userId)
            if (refreshResult.success) {
                state.tokenTestResult = "<span style='color:green'>✓ Tokens valid! User ID: ${userId.take(8)}...</span>"
            } else {
                state.tokenTestResult = "<span style='color:orange'>⚠ Access token decodes but refresh failed: ${refreshResult.error}</span>"
            }
        } else {
            state.tokenTestResult = "<span style='color:green'>✓ Access token format valid (User ID: ${userId.take(8)}...). Add refresh token to fully verify.</span>"
        }
    } catch (Exception e) {
        state.tokenTestResult = "<span style='color:red'>✗ Token test failed: ${e.message}</span>"
    }
}

def saveManualTokens() {
    logDebug "Saving manual tokens"
    state.tokenError = null
    state.tokenSuccess = null
    
    String accessToken = trimToken(settings.manualAccessToken)
    String refreshToken = trimToken(settings.manualRefreshToken)
    
    if (!accessToken || !refreshToken) {
        state.tokenError = "Both tokens are required"
        return
    }
    
    try {
        // Extract userId from JWT access token
        String userId = decodeJwtSub(accessToken)
        if (!userId) {
            state.tokenError = "Invalid access token - could not extract user ID"
            return
        }
        
        def refreshResult = refreshCognitoToken(refreshToken, userId)
        if (!refreshResult.success) {
            state.tokenError = "Refresh token invalid: ${refreshResult.error}"
            return
        }

        state.accessToken = refreshResult.accessToken
        state.idToken = refreshResult.idToken
        state.refreshToken = refreshToken
        state.userId = userId
        state.username = "Manual Entry"
        state.tokenExpiry = now() + (refreshResult.expiresIn * 1000)

        if (!establishWinixSession()) {
            state.tokenError = "Tokens saved but Winix session setup failed"
            return
        }

        state.tokenSuccess = true
        initialize()
        logDebug "Manual tokens saved successfully"
    } catch (Exception e) {
        state.tokenError = userFriendlyError(e.message)
        log.error "Token save error: ${e.message}"
    }
}

def refreshAuthToken() {
    logDebug "Refreshing auth token"
    
    if (!state.refreshToken || !state.userId) {
        log.warn "No refresh token available, please re-authenticate"
        return false
    }
    
    try {
        def result = refreshCognitoToken(state.refreshToken, state.userId)
        
        if (result.success) {
            state.accessToken = result.accessToken
            state.idToken = result.idToken
            state.tokenExpiry = now() + (result.expiresIn * 1000)
            state.winixUuid = generateWinixUuid(state.userId)
            if (!establishWinixSession()) {
                log.error "Token refreshed but Winix session setup failed"
                return false
            }
            logDebug "Token refreshed successfully"
            return true
        } else {
            log.error "Token refresh failed: ${result.error}"
            return false
        }
    } catch (Exception e) {
        log.error "Token refresh exception: ${e.message}"
        return false
    }
}

// ==================== SRP Authentication Implementation ====================

def authenticateWithSRP(String username, String password, int maxAttempts = 3) {
    String lastError = "Login failed"
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        logDebug "Starting SRP authentication (attempt ${attempt}/${maxAttempts})"
        def result = authenticateWithSRPOnce(username, password)
        if (result.success) {
            return result
        }
        lastError = result.error ?: lastError
        if (attempt < maxAttempts) {
            pauseExecution(3000)
        }
    }
    return [success: false, error: lastError]
}

def authenticateWithSRPOnce(String username, String password) {
    BigInteger bigN = new BigInteger(N_HEX, 16)
    BigInteger g = new BigInteger(G_HEX, 16)
    BigInteger k = hexToLong(hexHash("00" + N_HEX + "0" + G_HEX))

    BigInteger smallA = generateRandomSmallA(bigN)
    BigInteger largeA = g.modPow(smallA, bigN)

    if (largeA.mod(bigN) == BigInteger.ZERO) {
        return [success: false, error: "Safety check for A failed"]
    }

    String srpAHex = largeA.toString(16)

    def initiateParams = [
        AuthFlow: "USER_SRP_AUTH",
        ClientId: COGNITO_CLIENT_ID,
        AuthParameters: [
            USERNAME: username,
            SRP_A: srpAHex
        ]
    ]

    def initiateResponse = cognitoRequest("InitiateAuth", initiateParams)
    if (!initiateResponse.success) {
        return [success: false, error: initiateResponse.error]
    }

    if (initiateResponse.data.ChallengeName != "PASSWORD_VERIFIER") {
        return [success: false, error: "Unexpected challenge: ${initiateResponse.data.ChallengeName}"]
    }

    def challengeParams = initiateResponse.data.ChallengeParameters
    String userIdForSrp = challengeParams.USER_ID_FOR_SRP
    String saltHex = challengeParams.SALT
    String srpBHex = challengeParams.SRP_B
    String secretBlockB64 = challengeParams.SECRET_BLOCK

    BigInteger serverB = new BigInteger(srpBHex, 16)
    BigInteger salt = new BigInteger(saltHex, 16)
    BigInteger u = calculateU(largeA, serverB)

    if (u == BigInteger.ZERO) {
        return [success: false, error: "U value cannot be zero"]
    }

    String poolName = COGNITO_USER_POOL_ID.split("_")[1]
    String usernamePassword = "${poolName}${userIdForSrp}:${password}"
    String usernamePasswordHash = hashSha256(usernamePassword.getBytes("UTF-8"))
    BigInteger x = hexToLong(hexHash(padHex(salt) + usernamePasswordHash))

    BigInteger gModPowXN = g.modPow(x, bigN)
    BigInteger diff = serverB.subtract(k.multiply(gModPowXN))
    if (diff.signum() < 0) {
        diff = diff.add(bigN)
    }
    BigInteger s = diff.modPow(smallA.add(u.multiply(x)), bigN)

    byte[] hkdf = computeHkdf(hexToBytes(padHex(s)), hexToBytes(padHex(u)))
    String timestamp = formatTimestamp(new Date())

    byte[] secretBlock = secretBlockB64.decodeBase64()
    byte[] message = (poolName + userIdForSrp).getBytes("UTF-8")
    message = concatBytes(message, secretBlock)
    message = concatBytes(message, timestamp.getBytes("UTF-8"))
    String signature = hmacSha256Base64(hkdf, message)

    // winix-api warrant-lite: challenge USERNAME must be the login email, not USER_ID_FOR_SRP
    def challengeResponse = [
        ChallengeName: "PASSWORD_VERIFIER",
        ClientId: COGNITO_CLIENT_ID,
        ChallengeResponses: [
            USERNAME: username,
            TIMESTAMP: timestamp,
            PASSWORD_CLAIM_SECRET_BLOCK: secretBlockB64,
            PASSWORD_CLAIM_SIGNATURE: signature
        ]
    ]

    def authResponse = cognitoRequest("RespondToAuthChallenge", challengeResponse)
    if (!authResponse.success) {
        return [success: false, error: authResponse.error]
    }

    if (authResponse.data.ChallengeName == "NEW_PASSWORD_REQUIRED") {
        return [success: false, error: "Password change required - please change your password in the Winix app first"]
    }

    def authResult = authResponse.data.AuthenticationResult
    String userId = decodeJwtSub(authResult.AccessToken)

    return [
        success: true,
        accessToken: authResult.AccessToken,
        idToken: authResult.IdToken,
        refreshToken: authResult.RefreshToken,
        expiresIn: authResult.ExpiresIn,
        userId: userId
    ]
}

def refreshCognitoToken(String refreshToken, String userId) {
    def params = [
        AuthFlow: "REFRESH_TOKEN",
        ClientId: COGNITO_CLIENT_ID,
        AuthParameters: [
            REFRESH_TOKEN: refreshToken
        ]
    ]

    def response = cognitoRequest("InitiateAuth", params)
    if (!response.success) {
        def err = response.error ?: ""
        if (err.contains("NotAuthorizedException")) {
            return [success: false, error: "Refresh token has expired - please log in again"]
        }
        return [success: false, error: response.error]
    }

    def authResult = response.data.AuthenticationResult

    return [
        success: true,
        accessToken: authResult.AccessToken,
        idToken: authResult.IdToken,
        expiresIn: authResult.ExpiresIn
    ]
}

// ==================== Cognito API ====================

def cognitoRequest(String action, Map params) {
    String targetHeader = "AWSCognitoIdentityProviderService." + action
    String jsonBody = groovy.json.JsonOutput.toJson(params)
    
    logDebug "Cognito ${action} request"
    
    def requestParams = [
        uri: COGNITO_IDP_URL,
        requestContentType: "application/json",
        contentType: "application/json",
        headers: [
            "Content-Type": "application/x-amz-json-1.1",
            "X-Amz-Target": targetHeader
        ],
        body: jsonBody,
        timeout: 30
    ]
    
    try {
        def result = [success: false, error: "No response"]
        
        httpPost(requestParams) { resp ->
            logDebug "Cognito response status: ${resp.status}"
            if (resp.status == 200) {
                result = [success: true, data: resp.data]
            } else {
                result = [success: false, error: "HTTP ${resp.status}"]
            }
        }
        
        return result
    } catch (groovyx.net.http.HttpResponseException e) {
        String errorMessage = parseCognitoError(e)
        log.error "Cognito request failed: ${errorMessage}"
        return [success: false, error: errorMessage]
    } catch (Exception e) {
        log.error "Cognito exception: ${e.message}"
        logDebug "Exception details: ${e}"
        return [success: false, error: e.message ?: "Unknown error"]
    }
}

// ==================== SRP Helper Functions ====================

BigInteger generateRandomSmallA(BigInteger bigN) {
    // winix-api warrant-lite: 128 random bytes (Hubitat does not allow SecureRandom import)
    Random random = new Random()
    byte[] bytes = new byte[128]
    random.nextBytes(bytes)
    return new BigInteger(1, bytes).mod(bigN)
}

BigInteger hexToLong(String hexString) {
    return new BigInteger(hexString, 16)
}

String longToHex(BigInteger value) {
    return value.toString(16)
}

String padHex(BigInteger value) {
    return padHex(longToHex(value))
}

String padHex(String hexStr) {
    if (hexStr.length() % 2 == 1) {
        hexStr = "0" + hexStr
    }
    // If first nibble is high (8-F), prepend 00 (match winix-api warrant-lite)
    String firstChar = hexStr.substring(0, 1)
    if ("89ABCDEFabcdef".contains(firstChar)) {
        hexStr = "00" + hexStr
    }
    return hexStr
}

String hashSha256(byte[] data) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256")
    byte[] hash = digest.digest(data)
    return bytesToHex(hash).padLeft(64, '0')
}

String hexHash(String hexString) {
    return hashSha256(hexToBytes(hexString))
}

BigInteger calculateU(BigInteger bigA, BigInteger bigB) {
    String uHexHash = hexHash(padHex(bigA) + padHex(bigB))
    return hexToLong(uHexHash)
}

byte[] computeHkdf(byte[] ikm, byte[] salt) {
    // HKDF-Extract
    Mac mac = Mac.getInstance("HmacSHA256")
    SecretKeySpec keySpec = new SecretKeySpec(salt, "HmacSHA256")
    mac.init(keySpec)
    byte[] prk = mac.doFinal(ikm)
    
    // HKDF-Expand
    byte[] infoBitsWithCounter = concatBytes(INFO_BITS, [(byte)1] as byte[])
    keySpec = new SecretKeySpec(prk, "HmacSHA256")
    mac.init(keySpec)
    byte[] okm = mac.doFinal(infoBitsWithCounter)
    
    // Return first 16 bytes
    byte[] result = new byte[16]
    for (int i = 0; i < 16; i++) {
        result[i] = okm[i]
    }
    return result
}

String hmacSha256Base64(byte[] key, byte[] message) {
    Mac mac = Mac.getInstance("HmacSHA256")
    SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256")
    mac.init(keySpec)
    byte[] hmac = mac.doFinal(message)
    return hmac.encodeBase64().toString()
}

String formatTimestamp(Date date) {
    // Format: "Mon Jan 1 00:00:00 UTC 2024"
    def cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.setTime(date)
    
    def days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
    def months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
    
    String dayName = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    String monthName = months[cal.get(Calendar.MONTH)]
    int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    int hour = cal.get(Calendar.HOUR_OF_DAY)
    int minute = cal.get(Calendar.MINUTE)
    int second = cal.get(Calendar.SECOND)
    int year = cal.get(Calendar.YEAR)
    
    return String.format("%s %s %d %02d:%02d:%02d UTC %d", 
        dayName, monthName, dayOfMonth, hour, minute, second, year)
}

String decodeJwtSub(String jwt) {
    // JWT format: header.payload.signature
    String[] parts = jwt.split("\\.")
    if (parts.length < 2) return null
    
    String payload = parts[1]
    // Add padding if needed
    while (payload.length() % 4 != 0) {
        payload += "="
    }
    
    byte[] decoded = payload.decodeBase64()
    def json = new groovy.json.JsonSlurper().parseText(new String(decoded, "UTF-8"))
    return json.sub
}

// ==================== Byte Array Utilities ====================

byte[] hexToBytes(String hexString) {
    int len = hexString.length()
    byte[] data = new byte[len / 2]
    for (int i = 0; i < len; i += 2) {
        data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                             + Character.digit(hexString.charAt(i+1), 16))
    }
    return data
}

String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder()
    for (byte b : bytes) {
        sb.append(String.format("%02x", b))
    }
    return sb.toString()
}

byte[] concatBytes(byte[] a, byte[] b) {
    byte[] result = new byte[a.length + b.length]
    for (int i = 0; i < a.length; i++) {
        result[i] = a[i]
    }
    for (int i = 0; i < b.length; i++) {
        result[a.length + i] = b[i]
    }
    return result
}

// ==================== Winix Mobile Session (winix-api 2.0) ====================

def establishWinixSession() {
    if (!state.accessToken || !state.idToken || !state.userId) {
        log.error "establishWinixSession: missing tokens"
        return false
    }
    state.winixUuid = generateWinixUuid(state.userId)
    if (!resolveIdentityId(state.idToken)) {
        return false
    }
    if (!registerWinixUser()) {
        return false
    }
    if (!initWinixSession()) {
        return false
    }
    if (!checkWinixAccessToken()) {
        return false
    }
    return true
}

def resolveIdentityId(String idToken) {
    logDebug "Resolving Cognito identity id"
    def body = [
        IdentityPoolId: COGNITO_IDENTITY_POOL_ID,
        Logins: [(COGNITO_LOGINS_PROVIDER): idToken]
    ]
    def response = cognitoIdentityRequest("GetId", body)
    if (!response.success) {
        log.error "GetId failed: ${response.error}"
        return false
    }
    state.identityId = response.data.IdentityId
    logDebug "Resolved identityId"
    return state.identityId != null
}

def cognitoIdentityRequest(String action, Map params) {
    String targetHeader = "AWSCognitoIdentityService." + action
    def requestParams = [
        uri: COGNITO_IDENTITY_URL,
        requestContentType: "application/json",
        contentType: "application/json",
        headers: [
            "Content-Type": "application/x-amz-json-1.1",
            "X-Amz-Target": targetHeader
        ],
        body: groovy.json.JsonOutput.toJson(params),
        timeout: 30
    ]
    try {
        def result = [success: false, error: "No response"]
        httpPost(requestParams) { resp ->
            if (resp.status == 200) {
                result = [success: true, data: resp.data]
            } else {
                result = [success: false, error: "HTTP ${resp.status}"]
            }
        }
        return result
    } catch (groovyx.net.http.HttpResponseException e) {
        return [success: false, error: parseCognitoError(e)]
    } catch (Exception e) {
        return [success: false, error: e.message ?: "Unknown error"]
    }
}

def mobilePost(String url, Map payload) {
    byte[] encrypted = encryptMobilePayload(payload)
    def requestParams = [
        uri: url,
        requestContentType: "application/octet-stream",
        contentType: "application/octet-stream",
        headers: [
            "Accept": "application/octet-stream"
        ],
        body: encrypted,
        timeout: 30
    ]
    try {
        def result = [success: false, error: "No response"]
        httpPost(requestParams) { resp ->
            if (resp.status == 200) {
                def body = decryptMobileResponse(resp.data)
                if (body?.resultCode && body.resultCode != "200") {
                    result = [success: false, error: "resultCode ${body.resultCode}: ${body.resultMessage}"]
                } else {
                    result = [success: true, data: body]
                }
            } else {
                def body = decryptMobileResponse(resp.data)
                def code = body?.resultCode ?: "unknown"
                def msg = body?.resultMessage ?: "HTTP ${resp.status}"
                result = [success: false, error: "${code}: ${msg}"]
            }
        }
        return result
    } catch (Exception e) {
        return [success: false, error: e.message ?: "Mobile API error"]
    }
}

byte[] encryptMobilePayload(Map payload) {
    String json = groovy.json.JsonOutput.toJson(payload)
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE,
        new SecretKeySpec(hexToBytes(MOBILE_AES_KEY_HEX), "AES"),
        new IvParameterSpec(hexToBytes(MOBILE_AES_IV_HEX)))
    return cipher.doFinal(json.getBytes("UTF-8"))
}

Map decryptMobileResponse(def data) {
    if (!data) return null
    byte[] bytes = (data instanceof byte[]) ? data : data.toString().getBytes("ISO-8859-1")
    if (bytes.length == 0) return null
    try {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE,
            new SecretKeySpec(hexToBytes(MOBILE_AES_KEY_HEX), "AES"),
            new IvParameterSpec(hexToBytes(MOBILE_AES_IV_HEX)))
        String decrypted = new String(cipher.doFinal(bytes), "UTF-8")
        return new groovy.json.JsonSlurper().parseText(decrypted)
    } catch (Exception e) {
        logDebug "Could not decrypt mobile response: ${e.message}"
        return null
    }
}

String parseCognitoError(groovyx.net.http.HttpResponseException e) {
    try {
        def resp = e.response
        if (resp?.data != null) {
            String errorText = resp.data.toString()
            if (errorText?.contains("__type")) {
                def errorJson = new groovy.json.JsonSlurper().parseText(errorText)
                return (errorJson?.__type ?: "Error") + ": " + (errorJson?.message ?: errorText)
            }
            return errorText
        }
    } catch (Exception ignored) {}
    return "HTTP error: " + e.getStatusCode()
}

String getIdentityId() {
    return state.identityId
}

// ==================== Device Discovery & Management ====================

def discoverDevices() {
    logDebug "Discovering devices"
    state.discoverMessage = null

    if (!state.accessToken) {
        state.discoverMessage = "<span style='color:red'>Not authenticated. Log in on the main page first.</span>"
        log.error "Not authenticated"
        return
    }

    if (state.tokenExpiry && now() > state.tokenExpiry - 60000) {
        if (!refreshAuthToken()) {
            state.discoverMessage = "<span style='color:red'>Session expired. Please log in again.</span>"
            return
        }
    }

    try {
        def devices = getWinixDevices()
        state.discoveredDevices = devices ?: []
        if (devices) {
            state.discoverMessage = "<span style='color:green'>Found ${devices.size()} purifier(s). Toggle to add or remove.</span>"
            log.info "Discovered ${devices.size()} Winix device(s)"
        } else {
            state.discoverMessage = "<span style='color:orange'>No purifiers found on this account.</span>"
            log.warn "No devices found"
        }
    } catch (Exception e) {
        state.discoverMessage = "<span style='color:red'>Discovery failed: ${userFriendlyError(e.message)}</span>"
        log.error "Device discovery failed: ${e.message}"
    }
}


def refreshAllDevices() {
    logDebug "Refreshing all devices"
    getChildDevices().each { child ->
        child.refresh()
    }
}

// ==================== Winix API ====================

def getWinixDevices() {
    logDebug "Getting device list from Winix API"

    if (!state.winixUuid && state.userId) {
        state.winixUuid = generateWinixUuid(state.userId)
    }

    if (!state.identityId) {
        if (!establishWinixSession()) {
            log.error "Failed to establish Winix session before device list"
            return []
        }
    }

    def response = mobilePost("${MOBILE_API_BASE}/getDeviceInfoList", [
        accessToken: state.accessToken,
        uuid: state.winixUuid
    ])

    if (!response.success) {
        log.error "Failed to get devices: ${response.error}"
        return []
    }

    def devices = []
    response.data?.deviceInfoList?.each { deviceData ->
        devices << [
            deviceId: deviceData.deviceId,
            deviceAlias: deviceData.deviceAlias ?: deviceData.deviceId,
            modelName: deviceData.modelName ?: "Winix Purifier",
            mcuVer: deviceData.mcuVer
        ]
    }
    return devices
}

def registerWinixUser() {
    logDebug "Registering user with Winix API"
    def response = mobilePost("${MOBILE_API_BASE}/registerUser", [
        identityId: state.identityId,
        accessToken: state.accessToken,
        uuid: state.winixUuid,
        email: state.username,
        osType: "android",
        osVersion: "29",
        mobileLang: "en",
        appVersion: MOBILE_APP_VERSION,
        mobileModel: MOBILE_MODEL
    ])
    if (!response.success) {
        log.error "Failed to register user: ${response.error}"
    }
    return response.success
}

def initWinixSession() {
    logDebug "Winix mobile init"
    def response = mobilePost("${MOBILE_API_BASE}/init", [
        accessToken: state.accessToken,
        uuid: state.winixUuid,
        region: "US"
    ])
    if (!response.success) {
        log.error "Winix init failed: ${response.error}"
    }
    return response.success
}

def checkWinixAccessToken() {
    logDebug "Checking access token with Winix API"
    def response = mobilePost("${MOBILE_API_BASE}/checkAccessToken", [
        identityId: state.identityId,
        accessToken: state.accessToken,
        uuid: state.winixUuid,
        osVersion: "29",
        mobileLang: "en",
        appVersion: MOBILE_APP_VERSION,
        mobileModel: MOBILE_MODEL
    ])
    if (!response.success) {
        log.error "Failed to check access token: ${response.error}"
    }
    return response.success
}

// Generate Winix UUID: CRC32('github.com/regaw-leinad/winix-api' + userid) + CRC32('HGF' + userid)
String generateWinixUuid(String userId) {
    if (!userId) return ""
    
    String prefix1 = "github.com/regaw-leinad/winix-api"
    String prefix2 = "HGF"
    
    long crc1 = crc32(prefix1 + userId)
    long crc2 = crc32(prefix2 + userId)
    
    // Convert to hex strings and concatenate
    String hex1 = Long.toHexString(crc1 & 0xFFFFFFFFL)
    String hex2 = Long.toHexString(crc2 & 0xFFFFFFFFL)
    
    return hex1 + hex2
}

// CRC32 implementation
long crc32(String input) {
    byte[] bytes = input.getBytes("UTF-8")
    long crc = 0xFFFFFFFFL
    
    for (byte b : bytes) {
        crc = crc ^ (b & 0xFF)
        for (int i = 0; i < 8; i++) {
            if ((crc & 1) == 1) {
                crc = (crc >>> 1) ^ 0xEDB88320L
            } else {
                crc = crc >>> 1
            }
        }
    }
    
    return crc ^ 0xFFFFFFFFL
}

// API call helper for child devices
def apiGet(String path, Map query = [:]) {
    if (!state.accessToken) {
        log.error "Not authenticated"
        return null
    }
    
    // Ensure token is fresh
    if (state.tokenExpiry && now() > state.tokenExpiry - 60000) {
        refreshAuthToken()
    }
    
    def params = [
        uri: "${WINIX_API_BASE}${path}",
        headers: [
            "Authorization": "Bearer ${state.accessToken}",
            "Content-Type": "application/json"
        ],
        query: query,
        timeout: 30
    ]
    
    try {
        def result = null
        
        httpGet(params) { resp ->
            if (resp.status == 200) {
                result = resp.data
            }
        }
        
        return result
    } catch (Exception e) {
        log.error "API request failed: ${e.message}"
        return null
    }
}

// ==================== App helpers ====================

def getSetting(String name) {
    return settings?."${name}"
}

String trimToken(String token) {
    if (!token) return ""
    return token.trim().replaceAll(/\s+/, "")
}

String maskEmail(String email) {
    if (!email || !email.contains("@")) return "account"
    def parts = email.split("@", 2)
    def local = parts[0]
    def masked = local.length() <= 2 ? "**" : "${local.take(2)}***"
    return "${masked}@${parts[1]}"
}

String userFriendlyError(String message) {
    if (!message) return "An unknown error occurred"
    if (message.contains("NotAuthorizedException")) return "Invalid email or password"
    if (message.contains("UserNotFoundException")) return "No Winix account found for this email"
    if (message.contains("PasswordResetRequiredException")) return "Password reset required — update your password in the Winix app first"
    if (message.contains("TooManyRequestsException")) return "Too many attempts — wait a few minutes and try again"
    if (message.contains("CronExpression")) return "Invalid refresh schedule — use 60 minutes (hourly) or another supported interval"
    return message.length() > 200 ? message.take(200) + "..." : message
}

// ==================== Logging ====================

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}
