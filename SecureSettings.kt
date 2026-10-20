package com.aurora.media.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keep credentials out of plaintext SharedPreferences and out of exported backups. */
class SecureSettings(context: Context) {
    private val preferences = context.getSharedPreferences("aurora_connection", Context.MODE_PRIVATE)
    private val keyAlias = "aurora_credentials_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
        }.getOrDefault("")
    }

    fun load() = FtpSettings(
        host = preferences.getString("host", "").orEmpty(),
        port = preferences.getInt("port", 21),
        username = preferences.getString("username", "").orEmpty(),
        password = decrypt(preferences.getString("password", "").orEmpty()),
        directory = preferences.getString("directory", "/").orEmpty(),
        tmdbToken = decrypt(preferences.getString("tmdbToken", "").orEmpty())
    )

    fun save(settings: FtpSettings) {
        preferences.edit()
            .putString("host", settings.host.trim())
            .putInt("port", settings.port)
            .putString("username", settings.username)
            .putString("password", encrypt(settings.password))
            .putString("directory", settings.directory.trim().ifEmpty { "/" })
            .putString("tmdbToken", encrypt(settings.tmdbToken.trim()))
            .apply()
    }
}
