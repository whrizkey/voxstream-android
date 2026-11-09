package com.voxstream.android.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure persistent storage for API keys and provider endpoints using Android Keystore.
 */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "voxstream_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to private preferences if Keystore unavailable in emulator/test environment
        context.getSharedPreferences("voxstream_prefs", Context.MODE_PRIVATE)
    }

    var openAiApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()

    var openAiBaseUrl: String
        get() = prefs.getString(KEY_OPENAI_BASE_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(value) = prefs.edit().putString(KEY_OPENAI_BASE_URL, value).apply()

    var duckingAttenuationDb: Float
        get() = prefs.getFloat(KEY_DUCKING_DB, -14.0f)
        set(value) = prefs.edit().putFloat(KEY_DUCKING_DB, value).apply()

    var lookaheadBufferSeconds: Int
        get() = prefs.getInt(KEY_LOOKAHEAD_SECS, 60)
        set(value) = prefs.edit().putInt(KEY_LOOKAHEAD_SECS, value).apply()

    var maxCostUsd: Float
        get() = prefs.getFloat(KEY_MAX_COST_USD, 5.0f)
        set(value) = prefs.edit().putFloat(KEY_MAX_COST_USD, value).apply()

    companion object {
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_OPENAI_BASE_URL = "openai_base_url"
        private const val KEY_DUCKING_DB = "ducking_attenuation_db"
        private const val KEY_LOOKAHEAD_SECS = "lookahead_seconds"
        private const val KEY_MAX_COST_USD = "max_cost_usd"
    }
}
