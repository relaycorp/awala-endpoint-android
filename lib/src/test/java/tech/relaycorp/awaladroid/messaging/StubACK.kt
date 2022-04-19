package tech.relaycorp.awaladroid.messaging

internal class StubACK {
    var wasCalled: Boolean = false
        private set

    @Suppress("RedundantSuspendModifier")
    suspend fun run() {
        wasCalled = true
    }
}
