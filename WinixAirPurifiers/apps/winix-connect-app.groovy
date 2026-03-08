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
 *  v1.0   03-07-26   Initial release
 *
 */

import groovy.transform.Field
import java.security.MessageDigest
import javax.crypto.Mac
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
// From parent project https://github.com/regaw-leinad/winix-api
// Pulled from Winix Home v1.0.8 (Android) APK - https://github.com/hfern/winix
@Field static final String COGNITO_REGION = "us-east-1"
@Field static final String COGNITO_USER_POOL_ID = "us-east-1_Ofd50EosD"
@Field static final String COGNITO_CLIENT_ID = "14og512b9u20b8vrdm55d8empi"
@Field static final String COGNITO_CLIENT_SECRET = "k554d4pvgf2n0chbhgtmbe4q0ul4a9flp3pcl6a47ch6rripvvr"

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
@Field static final String COGNITO_IDP_URL = "https://cognito-idp.us-east-1.amazonaws.com"

// ==================== App Pages ====================

def mainPage() {
    dynamicPage(name: "mainPage", title: "Manage Your Winix Air Purifiers", install: true, uninstall: true) {
        // Section 1: Authentication
        section("Authentication") {
            if (state.accessToken) {
                paragraph "✓ Connected to Winix"
                href "loginPage", title: "Re-authenticate", description: "Log in with username/password"
                href "tokenEntryPage", title: "Manual Token Entry", description: "Enter tokens manually"
            } else {
                paragraph "Please log in to your Winix account to get started."
                href "loginPage", title: "Log In", description: "Connect your Winix account"
                href "tokenEntryPage", title: "Manual Token Entry", description: "Enter tokens manually if login fails"
            }
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
                input name: "cacheIntervalSeconds", type: "number", title: "API Cache Interval (seconds)", 
                      defaultValue: 60, range: "10..300"
                input name: "deviceRefreshIntervalMinutes", type: "number", title: "Device Refresh Interval (minutes)", 
                      defaultValue: 60, range: "1..1440"
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
            paragraph "If automatic login isn't working, you can manually enter tokens from the get-tokens tool."
            paragraph "<b>Instructions:</b><br>1. Run: <code>node get-tokens.mjs</code><br>2. Enter your Winix credentials<br>3. Copy the tokens below"
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
        // Schedule token refresh (tokens expire in ~1 hour, refresh at 50 minutes)
        runEvery1Hour(refreshAuthToken)
        
        // Schedule device refresh based on settings
        def refreshMinutes = deviceRefreshIntervalMinutes ?: 60
        schedule("0 */${refreshMinutes} * ? * *", refreshAllDevices)
    }
}

def uninstalled() {
    logDebug "Uninstalled"
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

// ==================== Authentication ====================

def performLogin() {
    String username = settings.winixUsername
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
        def authResult = authenticateWithSRP(username, password)
        
        if (authResult.success) {
            state.username = username
            state.accessToken = authResult.accessToken
            state.refreshToken = authResult.refreshToken
            state.userId = authResult.userId
            state.tokenExpiry = now() + (authResult.expiresIn * 1000)
            state.loginSuccess = true
            logDebug "Login successful"
        } else {
            state.loginError = authResult.error ?: "Login failed"
            log.error "Login failed: ${state.loginError}"
        }
    } catch (Exception e) {
        state.loginError = "Login error: ${e.message}"
        log.error "Login exception: ${e.message}"
    }
}

def testManualToken() {
    logDebug "Testing manual token"
    state.tokenTestResult = null
    
    String accessToken = settings.manualAccessToken
    
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
        
        // Step 2: Try to make an API call to verify the token
        String deviceUuid = generateWinixUuid(userId)
        
        def testParams = [
            uri: "https://us.mobile.winix-iot.com/checkAccessToken",
            contentType: "application/json",
            body: [
                cognitoClientSecretKey: COGNITO_CLIENT_SECRET,
                accessToken: accessToken,
                uuid: deviceUuid,
                osVersion: "29",
                mobileLang: "en"
            ],
            timeout: 15
        ]
        
        def testResult = false
        httpPost(testParams) { resp ->
            if (resp.status == 200) {
                testResult = true
            }
        }
        
        if (testResult) {
            state.tokenTestResult = "<span style='color:green'>✓ Token is valid! User ID: ${userId.take(8)}...</span>"
        } else {
            state.tokenTestResult = "<span style='color:orange'>⚠ Token decoded but API verification failed - token may be expired</span>"
        }
    } catch (Exception e) {
        state.tokenTestResult = "<span style='color:red'>✗ Token test failed: ${e.message}</span>"
    }
}

def saveManualTokens() {
    logDebug "Saving manual tokens"
    state.tokenError = null
    state.tokenSuccess = null
    
    String accessToken = settings.manualAccessToken
    String refreshToken = settings.manualRefreshToken
    
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
        
        // Store tokens
        state.accessToken = accessToken
        state.refreshToken = refreshToken
        state.userId = userId
        state.username = "Manual Entry"
        state.tokenExpiry = now() + (3600 * 1000)  // Assume 1 hour expiry
        state.tokenSuccess = true
        
        logDebug "Manual tokens saved successfully"
    } catch (Exception e) {
        state.tokenError = "Error saving tokens: ${e.message}"
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
            state.tokenExpiry = now() + (result.expiresIn * 1000)
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

def authenticateWithSRP(String username, String password) {
    logDebug "Starting SRP authentication"
    
    // Initialize SRP values
    BigInteger bigN = new BigInteger(N_HEX, 16)
    BigInteger g = new BigInteger(G_HEX, 16)
    BigInteger k = hexToLong(hexHash("00" + N_HEX + "0" + G_HEX))
    
    // Generate random small 'a' value (128 bytes = 1024 bits)
    BigInteger smallA = generateRandomSmallA(bigN)
    
    // Calculate large 'A' value: A = g^a mod N
    BigInteger largeA = g.modPow(smallA, bigN)
    
    if (largeA.mod(bigN) == BigInteger.ZERO) {
        return [success: false, error: "Safety check for A failed"]
    }
    
    // Calculate secret hash
    String secretHash = calculateSecretHash(username, COGNITO_CLIENT_ID, COGNITO_CLIENT_SECRET)
    
    // Get hex string of A (uppercase to match some AWS implementations)
    String srpAHex = largeA.toString(16)
    
    logDebug "Initiating SRP auth flow"
    
    // Step 1: Initiate auth with USER_SRP_AUTH
    def initiateParams = [
        AuthFlow: "USER_SRP_AUTH",
        ClientId: COGNITO_CLIENT_ID,
        AuthParameters: [
            USERNAME: username,
            SRP_A: srpAHex,
            SECRET_HASH: secretHash
        ]
    ]
    
    // Log the JSON to verify structure
    
    def initiateResponse = cognitoRequest("InitiateAuth", initiateParams)
    
    if (!initiateResponse.success) {
        return [success: false, error: initiateResponse.error]
    }
    
    def challengeParams = initiateResponse.data.ChallengeParameters
    
    if (initiateResponse.data.ChallengeName != "PASSWORD_VERIFIER") {
        return [success: false, error: "Unexpected challenge: ${initiateResponse.data.ChallengeName}"]
    }
    
    // Step 2: Process the challenge
    String userIdForSrp = challengeParams.USER_ID_FOR_SRP
    String saltHex = challengeParams.SALT
    String srpBHex = challengeParams.SRP_B
    String secretBlockB64 = challengeParams.SECRET_BLOCK
    
    BigInteger serverB = new BigInteger(srpBHex, 16)
    BigInteger salt = new BigInteger(saltHex, 16)
    
    // Calculate u = H(A || B)
    BigInteger u = calculateU(largeA, serverB)
    
    if (u == BigInteger.ZERO) {
        return [success: false, error: "U value cannot be zero"]
    }
    
    // Get the pool ID without region prefix
    String poolName = COGNITO_USER_POOL_ID.split("_")[1]
    
    // Calculate x = H(salt || H(poolName || username || ":" || password))
    String usernamePassword = "${poolName}${userIdForSrp}:${password}"
    String usernamePasswordHash = hashSha256(usernamePassword.getBytes("UTF-8"))
    BigInteger x = hexToLong(hexHash(padHex(salt) + usernamePasswordHash))
    
    // Calculate S = (B - k * g^x)^(a + u * x) mod N
    BigInteger gModPowXN = g.modPow(x, bigN)
    BigInteger kgx = k.multiply(gModPowXN)
    BigInteger diff = serverB.subtract(kgx)
    // Handle negative difference
    if (diff.signum() < 0) {
        diff = diff.add(bigN)
    }
    BigInteger exponent = smallA.add(u.multiply(x))
    BigInteger s = diff.modPow(exponent, bigN)
    
    // Compute HKDF
    byte[] hkdf = computeHkdf(
        hexToBytes(padHex(s)),
        hexToBytes(padHex(u))
    )
    
    // Generate timestamp
    String timestamp = formatTimestamp(new Date())
    
    // Calculate signature
    byte[] secretBlock = secretBlockB64.decodeBase64()
    byte[] message = (poolName + userIdForSrp).getBytes("UTF-8")
    message = concatBytes(message, secretBlock)
    message = concatBytes(message, timestamp.getBytes("UTF-8"))
    
    String signature = hmacSha256Base64(hkdf, message)
    
    // Calculate SECRET_HASH for the challenge response using userIdForSrp
    String challengeSecretHash = calculateSecretHash(userIdForSrp, COGNITO_CLIENT_ID, COGNITO_CLIENT_SECRET)
    
    // Step 3: Respond to auth challenge
    def challengeResponse = [
        ChallengeName: "PASSWORD_VERIFIER",
        ClientId: COGNITO_CLIENT_ID,
        ChallengeResponses: [
            USERNAME: userIdForSrp,
            TIMESTAMP: timestamp,
            PASSWORD_CLAIM_SECRET_BLOCK: secretBlockB64,
            PASSWORD_CLAIM_SIGNATURE: signature,
            SECRET_HASH: challengeSecretHash
        ]
    ]
    
    logDebug "Calling RespondToAuthChallenge"
    def authResponse = cognitoRequest("RespondToAuthChallenge", challengeResponse)
    
    if (!authResponse.success) {
        return [success: false, error: authResponse.error]
    }
    
    if (authResponse.data.ChallengeName == "NEW_PASSWORD_REQUIRED") {
        return [success: false, error: "Password change required - please change your password in the Winix app first"]
    }
    
    def authResult = authResponse.data.AuthenticationResult
    
    // Decode JWT to get user ID
    String userId = decodeJwtSub(authResult.AccessToken)
    
    return [
        success: true,
        accessToken: authResult.AccessToken,
        refreshToken: authResult.RefreshToken,
        expiresIn: authResult.ExpiresIn,
        userId: userId
    ]
}

def refreshCognitoToken(String refreshToken, String userId) {
    String secretHash = calculateSecretHash(userId, COGNITO_CLIENT_ID, COGNITO_CLIENT_SECRET)
    
    def params = [
        AuthFlow: "REFRESH_TOKEN",
        ClientId: COGNITO_CLIENT_ID,
        AuthParameters: [
            REFRESH_TOKEN: refreshToken,
            SECRET_HASH: secretHash
        ]
    ]
    
    def response = cognitoRequest("InitiateAuth", params)
    
    if (!response.success) {
        return [success: false, error: response.error]
    }
    
    def authResult = response.data.AuthenticationResult
    
    return [
        success: true,
        accessToken: authResult.AccessToken,
        expiresIn: authResult.ExpiresIn
    ]
}

// ==================== Cognito API ====================

def cognitoRequest(String action, Map params) {
    String targetHeader = "AWSCognitoIdentityProviderService." + action
    String jsonBody = groovy.json.JsonOutput.toJson(params)
    
    logDebug "Cognito ${action} request"
    
    def requestParams = [
        uri: "https://cognito-idp.us-east-1.amazonaws.com/",
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
        String errorMessage = "HTTP error: " + e.getStatusCode()
        
        try {
            def resp = e.response
            logDebug "HTTP error status: ${e.getStatusCode()}"
            if (resp?.data != null) {
                String errorText = resp.data.toString()
                logDebug "Error response: ${errorText}"
                if (errorText?.contains("__type")) {
                    def errorJson = new groovy.json.JsonSlurper().parseText(errorText)
                    errorMessage = (errorJson?.__type ?: "Error") + ": " + (errorJson?.message ?: errorText)
                } else {
                    errorMessage = errorText
                }
            }
        } catch (Exception parseEx) {
            logDebug "Could not parse error: ${parseEx.message}"
        }
        
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
    // Generate 256 bytes of random data (matching Node.js implementation)
    Random random = new Random()
    byte[] bytes = new byte[256]
    random.nextBytes(bytes)
    BigInteger randomValue = new BigInteger(1, bytes)
    return randomValue.mod(bigN)
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
    // If first character is high (8-F), prepend 00
    String firstChar = hexStr.substring(0, 1).toUpperCase()
    if ("89ABCDEF".contains(firstChar)) {
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

String calculateSecretHash(String username, String clientId, String clientSecret) {
    String message = username + clientId
    return hmacSha256Base64(clientSecret.getBytes("UTF-8"), message.getBytes("UTF-8"))
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

// ==================== Device Discovery & Management ====================

def discoverDevices() {
    logDebug "Discovering devices"
    
    if (!state.accessToken) {
        log.error "Not authenticated"
        return
    }
    
    // Ensure token is fresh
    if (state.tokenExpiry && now() > state.tokenExpiry - 60000) {
        refreshAuthToken()
    }
    
    try {
        def devices = getWinixDevices()
        if (devices) {
            state.discoveredDevices = devices
            logDebug "Discovered ${devices.size()} devices"
        } else {
            log.warn "No devices found"
            state.discoveredDevices = []
        }
    } catch (Exception e) {
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
    
    // Generate UUID from userId using CRC32
    String deviceUuid = generateWinixUuid(state.userId)
    logDebug "Generated Winix UUID: ${deviceUuid}"
    
    // Store for later use
    state.winixUuid = deviceUuid
    
    // Step 1: Register user with Winix API
    if (!registerWinixUser(deviceUuid)) {
        log.error "Failed to register user with Winix API"
        return []
    }
    
    // Step 2: Check access token
    if (!checkWinixAccessToken(deviceUuid)) {
        log.error "Failed to validate access token with Winix API"
        return []
    }
    
    // Step 3: Get device list
    def bodyContent = [
        accessToken: state.accessToken,
        uuid: deviceUuid
    ]
    
    def params = [
        uri: "https://us.mobile.winix-iot.com/getDeviceInfoList",
        requestContentType: "application/json",
        contentType: "application/json",
        body: groovy.json.JsonOutput.toJson(bodyContent),
        timeout: 30
    ]
    
    try {
        def devices = []
        
        httpPost(params) { resp ->
            logDebug "Device list response status: ${resp.status}"
            
            if (resp.status == 200 && resp.data?.deviceInfoList) {
                resp.data.deviceInfoList.each { deviceData ->
                    devices << [
                        deviceId: deviceData.deviceId,
                        deviceAlias: deviceData.deviceAlias ?: deviceData.deviceId,
                        modelName: deviceData.modelName ?: "Winix Purifier",
                        mcuVer: deviceData.mcuVer
                    ]
                }
            }
        }
        
        return devices
    } catch (Exception e) {
        log.error "Failed to get devices: ${e.message}"
        return []
    }
}

def registerWinixUser(String uuid) {
    logDebug "Registering user with Winix API"
    
    def bodyContent = [
        cognitoClientSecretKey: COGNITO_CLIENT_SECRET,
        accessToken: state.accessToken,
        uuid: uuid,
        email: state.username,
        osType: "android",
        osVersion: "29",
        mobileLang: "en"
    ]
    
    def params = [
        uri: "https://us.mobile.winix-iot.com/registerUser",
        requestContentType: "application/json",
        contentType: "application/json",
        body: groovy.json.JsonOutput.toJson(bodyContent),
        timeout: 30
    ]
    
    try {
        def success = false
        httpPost(params) { resp ->
            logDebug "Register user response status: ${resp.status}"
            if (resp.status == 200) {
                success = true
            }
        }
        return success
    } catch (Exception e) {
        log.error "Failed to register user: ${e.message}"
        return false
    }
}

def checkWinixAccessToken(String uuid) {
    logDebug "Checking access token with Winix API"
    
    def bodyContent = [
        cognitoClientSecretKey: COGNITO_CLIENT_SECRET,
        accessToken: state.accessToken,
        uuid: uuid,
        osVersion: "29",
        mobileLang: "en"
    ]
    
    def params = [
        uri: "https://us.mobile.winix-iot.com/checkAccessToken",
        requestContentType: "application/json",
        contentType: "application/json",
        body: groovy.json.JsonOutput.toJson(bodyContent),
        timeout: 30
    ]
    
    try {
        def success = false
        httpPost(params) { resp ->
            logDebug "Check access token response status: ${resp.status}"
            if (resp.status == 200) {
                success = true
            }
        }
        return success
    } catch (Exception e) {
        log.error "Failed to check access token: ${e.message}"
        return false
    }
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

// ==================== Logging ====================

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}
