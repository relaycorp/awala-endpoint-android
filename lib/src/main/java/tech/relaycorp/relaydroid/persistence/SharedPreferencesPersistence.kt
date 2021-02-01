package tech.relaycorp.relaydroid.persistence

import android.content.Context
import android.util.Base64

class SharedPreferencesPersistence(
    context: Context
) : Persistence {
    private val preferences by lazy {
        context.getSharedPreferences("relaynet_sdk", Context.MODE_PRIVATE)
    }

    override suspend fun set(location: String, data: ByteArray) {
        preferences.edit()
            .putString(location, data.encode())
            .apply()
    }

    override suspend fun get(location: String): ByteArray? =
        preferences.getString(location, null)?.decode()

    override suspend fun delete(location: String) {
        preferences.edit()
            .remove(location)
            .apply()
    }

    override suspend fun deleteAll() {
        preferences.edit().clear().apply()
    }

    override suspend fun list(locationPrefix: String) =
        preferences.all.keys.filter { it.startsWith(locationPrefix) }

    private fun String.decode() =
        Base64.decode(encodeToByteArray(), Base64.DEFAULT)

    private fun ByteArray.encode() =
        Base64.encode(this, Base64.DEFAULT).decodeToString()
}
