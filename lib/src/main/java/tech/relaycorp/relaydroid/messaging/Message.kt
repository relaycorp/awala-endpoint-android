package tech.relaycorp.relaydroid.messaging

/**
 * A service message.
 *
 * @property parcelId The parcel id.
 */
public abstract class Message internal constructor(public val parcelId: ParcelId)
