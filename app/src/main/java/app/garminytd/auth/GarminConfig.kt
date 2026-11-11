package app.garminytd.auth

import java.net.URLEncoder

/**
 * Endpoints and client identities for Garmin Connect's mobile login.
 *
 * Garmin has no public API for individual users. These values mirror what the open-source
 * python-garminconnect library (0.3.20, Oct 2026) uses after Garmin retired the old OAuth1 flow
 * in March 2026: mobile SSO page -> CAS service ticket (ST-...) -> "DI" OAuth2 token at diauth.
 *
 * If Garmin changes things again, this file is the one place to update.
 */
object GarminConfig {
    /** SSO client the login page is opened as. Must match [SERVICE_URL]. */
    const val SSO_CLIENT_ID = "GCM_IOS_DARK"

    /** CAS "service" the ticket is issued for; must be repeated verbatim in the token exchange. */
    const val SERVICE_URL = "https://mobile.integration.garmin.com/gcm/ios"

    /** Garmin's page in German for German-speaking users (verified to serve the same form), else English. */
    fun signInUrl(locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val path = if (locale.language == "de") "de_DE" else "en_US"
        return "https://sso.garmin.com/mobile/sso/$path/sign-in?clientId=$SSO_CLIENT_ID&service=" +
            URLEncoder.encode(SERVICE_URL, "UTF-8")
    }

    const val DI_TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token"
    const val DI_GRANT_SERVICE_TICKET =
        "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"

    /** Tried in order; the first one Garmin accepts wins and is stored for refreshes. */
    val DI_CLIENT_IDS = listOf(
        "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
        "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
        "GARMIN_CONNECT_MOBILE_ANDROID_DI",
        "GARMIN_CONNECT_MOBILE_IOS_DI",
    )

    const val CONNECT_API = "https://connectapi.garmin.com"

    /** Headers the Garmin Connect Android app sends; connectapi checks them. */
    val NATIVE_HEADERS: Map<String, String> = mapOf(
        "User-Agent" to "GCM-Android-5.23",
        "X-Garmin-User-Agent" to
            "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0",
        "X-Garmin-Paired-App-Version" to "10861",
        "X-Garmin-Client-Platform" to "Android",
        "X-App-Ver" to "10861",
        "X-Lang" to "en",
        "X-GCExperience" to "GC5",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    /** CAS service tickets look like ST-0123456-AbCdEf...-cas. */
    val TICKET_REGEX = Regex("""ST-[A-Za-z0-9._\-]+""")
}
