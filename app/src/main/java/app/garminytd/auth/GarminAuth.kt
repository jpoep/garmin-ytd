package app.garminytd.auth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class GarminAuthException(message: String) : Exception(message) {
    /** Garmin answered 427/429: the client is being blocked, retrying immediately makes it worse. */
    class Blocked(val code: Int) : GarminAuthException("Garmin is rate-limiting logins (HTTP $code). Wait a while and try again.")
    /** Refresh token expired/revoked (~30 days unused) – user must log in again. */
    class SessionExpired : GarminAuthException("Your Garmin session expired. Please connect again.")
    /** [message] is a short technical detail; the UI wraps it in a localized sentence. */
    class Failed(message: String) : GarminAuthException(message)
}

/**
 * Turns a CAS service ticket into a Garmin "DI" OAuth2 token and keeps it fresh.
 *
 * Access tokens live ~25 h; refresh tokens ~30 days and rotate on every refresh, so as long as the
 * app is opened once a month the user never has to log in again.
 */
class GarminAuth(
    private val http: OkHttpClient,
    private val store: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var cached: DiToken? = store.load()

    val isLoggedIn: Boolean get() = cached != null

    suspend fun loginWithTicket(ticket: String): DiToken = withContext(Dispatchers.IO) {
        require(GarminConfig.TICKET_REGEX.matches(ticket)) { "Not a CAS service ticket" }
        var lastError = "no client id accepted"
        for (clientId in GarminConfig.DI_CLIENT_IDS) {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("service_ticket", ticket)
                .add("grant_type", GarminConfig.DI_GRANT_SERVICE_TICKET)
                .add("service_url", GarminConfig.SERVICE_URL)
                .build()
            http.newCall(tokenRequest(clientId, body)).execute().use { resp ->
                if (resp.code == 427 || resp.code == 429) throw GarminAuthException.Blocked(resp.code)
                if (resp.isSuccessful) {
                    val token = parseToken(resp.body.string(), clientId, previousRefresh = null)
                    persist(token)
                    return@withContext token
                }
                lastError = "HTTP ${resp.code} for $clientId"
            }
        }
        throw GarminAuthException.Failed("login ticket rejected, $lastError")
    }

    /** Returns a token valid for at least 15 more minutes, refreshing if needed. */
    suspend fun validAccessToken(): String = mutex.withLock {
        val current = cached ?: throw GarminAuthException.SessionExpired()
        if (nowSec() < current.expiresAt - REFRESH_MARGIN_SEC) return current.accessToken
        refreshLocked(current).accessToken
    }

    /** Forces a refresh, used after the API answers 401 with a token we thought was valid. */
    suspend fun forceRefresh(): String = mutex.withLock {
        val current = cached ?: throw GarminAuthException.SessionExpired()
        refreshLocked(current).accessToken
    }

    fun logout() {
        cached = null
        store.clear()
    }

    private suspend fun refreshLocked(current: DiToken): DiToken = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", current.clientId)
            .add("refresh_token", current.refreshToken)
            .build()
        http.newCall(tokenRequest(current.clientId, body)).execute().use { resp ->
            when {
                resp.code == 427 || resp.code == 429 -> throw GarminAuthException.Blocked(resp.code)
                resp.code == 400 || resp.code == 401 -> {
                    logout()
                    throw GarminAuthException.SessionExpired()
                }
                !resp.isSuccessful -> throw GarminAuthException.Failed("token refresh, HTTP ${resp.code}")
            }
            val token = parseToken(resp.body.string(), current.clientId, current.refreshToken)
            persist(token)
            token
        }
    }

    private fun tokenRequest(clientId: String, body: FormBody): Request {
        val basic = "Basic " + Base64.encodeToString("$clientId:".encodeToByteArray(), Base64.NO_WRAP)
        return Request.Builder()
            .url(GarminConfig.DI_TOKEN_URL)
            .apply { GarminConfig.NATIVE_HEADERS.forEach { (k, v) -> header(k, v) } }
            .header("Authorization", basic)
            .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
            .header("Cache-Control", "no-cache")
            .post(body)
            .build()
    }

    private fun parseToken(body: String, fallbackClientId: String, previousRefresh: String?): DiToken {
        val obj = json.parseToJsonElement(body).jsonObject
        val access = obj.str("access_token") ?: throw GarminAuthException.Failed("no access_token in response")
        val refresh = obj.str("refresh_token") ?: previousRefresh
            ?: throw GarminAuthException.Failed("no refresh_token in response")
        val claims = JwtClaims.parse(access)
        val expiresAt = claims?.exp
            ?: obj["expires_in"]?.jsonPrimitive?.longOrNull?.let { nowSec() + it }
            ?: (nowSec() + 3600)
        return DiToken(
            accessToken = access,
            refreshToken = refresh,
            // Garmin may hand back a token for a different client id than the one we asked with.
            clientId = claims?.clientId ?: fallbackClientId,
            expiresAt = expiresAt,
        )
    }

    private fun persist(token: DiToken) {
        cached = token
        store.save(token)
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun nowSec() = System.currentTimeMillis() / 1000

    private companion object {
        const val REFRESH_MARGIN_SEC = 15 * 60
    }
}

/** Minimal, unverified JWT payload reader – we only need exp and client_id for our own bookkeeping. */
internal data class JwtClaims(val exp: Long?, val clientId: String?) {
    companion object {
        fun parse(jwt: String): JwtClaims? = runCatching {
            val payload = jwt.split('.')[1]
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).decodeToString()
            val obj = Json.parseToJsonElement(decoded).jsonObject
            JwtClaims(
                exp = obj["exp"]?.jsonPrimitive?.longOrNull,
                clientId = obj["client_id"]?.jsonPrimitive?.content,
            )
        }.getOrNull()
    }
}
