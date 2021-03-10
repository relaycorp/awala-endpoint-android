package tech.relaycorp.relaydroid.messaging

import java.util.UUID

/**
 * The id of a parcel.
 *
 * @property value The string representation of the parcel.
 *
 * You should only ever use these ids if you wish to replace an in-transit parcel. That is, if you
 * wish to replace a parcel currently held by a gateway. If the older parcel already reached the
 * final destination, subsequent parcels with the same id will be ignored.
 *
 * Note that the behavior above is scoped to the same sender/recipient pair.
 */
public class ParcelId
internal constructor(
    public val value: String
) {
    public companion object {
        /**
         * Generate a new parcel id.
         */
        public fun generate(): ParcelId = ParcelId(UUID.randomUUID().toString())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParcelId) return false
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int = value.hashCode()
}
