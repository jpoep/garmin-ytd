package app.garminytd.auth

import androidx.core.content.edit
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class DiToken(
    val accessToken: String,
    val refreshToken: String,
    val clientId: String,
    /** Epoch seconds when [accessToken] expires. */
    val expiresAt: Long,
)

/**
 * Persists the Garmin token encrypted with an AES-GCM key that lives in the Android Keystore
 * (hardware-backed where available, never exportable). Plain Keystore instead of the deprecated
 * androidx.security-crypto library.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("garmin_auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): DiToken? {
        val blob = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_LEN))
            val plain = cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN)
            json.decodeFromString(DiToken.serializer(), plain.decodeToString())
        }.getOrElse {
            // Key invalidated (e.g. device restore) or corrupt blob: force a fresh login.
            clear()
            null
        }
    }

    fun save(token: DiToken) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val enc = cipher.doFinal(json.encodeToString(DiToken.serializer(), token).encodeToByteArray())
        val blob = cipher.iv + enc
        prefs.edit { putString(KEY_TOKEN, Base64.encodeToString(blob, Base64.NO_WRAP)) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "garmin_token_key"
        const val KEY_TOKEN = "token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
