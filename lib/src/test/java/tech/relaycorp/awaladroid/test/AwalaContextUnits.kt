package tech.relaycorp.awaladroid.test

import kotlinx.coroutines.CompletableDeferred
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.AwalaContext

internal fun setAwalaContext(context: AwalaContext) {
    Awala.contextDeferred = CompletableDeferred(context)
}

internal fun unsetAwalaContext() {
    Awala.contextDeferred = CompletableDeferred()
}
