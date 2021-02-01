package tech.relaycorp.relaydroid

import java.util.UUID

class MessageId
internal constructor(
    val value: String
) {
    companion object {
        fun generate() = MessageId(UUID.randomUUID().toString())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageId) return false
        if (value != other.value) return false
        return true
    }

    override fun hashCode() = value.hashCode()
}
