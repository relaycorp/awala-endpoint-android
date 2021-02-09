package tech.relaycorp.relaydroid

import java.util.UUID

public class MessageId
internal constructor(
    public val value: String
) {
    public companion object {
        public fun generate(): MessageId = MessageId(UUID.randomUUID().toString())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageId) return false
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int = value.hashCode()
}
