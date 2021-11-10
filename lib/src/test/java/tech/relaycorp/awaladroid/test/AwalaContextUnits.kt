package tech.relaycorp.awaladroid.test

import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.AwalaContext

internal fun setAwalaContext(context: AwalaContext) {
    Awala.context = context
}

internal fun unsetAwalaContext() {
    Awala.context = null
}
