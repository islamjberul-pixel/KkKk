package com.kar7.pro.security

import android.content.Context
import android.content.SharedPreferences
import com.kar7.pro.models.ApiErrorDetailAdapter
import com.kar7.pro.models.StoredSession
import com.squareup.moshi.Moshi

class SecureSessionStore(
    context: Context,
    private val cipherEngine: CipherEngine = AndroidKeystoreCipherEngine(),
    moshi: Moshi = Moshi.Builder().add(ApiErrorDetailAdapter()).build()
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val adapter = moshi.adapter(StoredSession::class.java)

    fun saveSession(session: StoredSession): Boolean {
        return try {
            val json = adapter.toJson(session)
            val encrypted = cipherEngine.encrypt(json)
            prefs.edit().putString(KEY_ENCRYPTED_SESSION, encrypted).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadSession(): StoredSession? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_SESSION, null) ?: return null
        val decrypted = cipherEngine.decrypt(encrypted) ?: run {
            clearSession()
            return null
        }
        return try {
            adapter.fromJson(decrypted)
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    fun clearSession() {
        prefs.edit().remove(KEY_ENCRYPTED_SESSION).apply()
    }

    fun saveSafTreeUri(uriString: String?) {
        val encrypted = uriString?.let { runCatching { cipherEngine.encrypt(it) }.getOrNull() }
        if (encrypted == null) {
            prefs.edit().remove(KEY_SAF_TREE_URI).apply()
        } else {
            prefs.edit().putString(KEY_SAF_TREE_URI, encrypted).apply()
        }
    }

    fun loadSafTreeUri(): String? {
        val encrypted = prefs.getString(KEY_SAF_TREE_URI, null) ?: return null
        return cipherEngine.decrypt(encrypted)
    }

    companion object {
        private const val PREFS_NAME = "kar7_secure_session"
        private const val KEY_ENCRYPTED_SESSION = "encrypted_session_blob"
        private const val KEY_SAF_TREE_URI = "encrypted_saf_tree_uri"
    }
}
