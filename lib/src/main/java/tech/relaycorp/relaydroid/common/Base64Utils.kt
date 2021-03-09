package tech.relaycorp.relaydroid.common

import android.util.Base64

internal fun String.decodeBase64() =
    Base64.decode(this, Base64.DEFAULT)

internal fun ByteArray.encodeBase64() =
    Base64.encode(this, Base64.DEFAULT)
