package tech.relaycorp.awaladroid.storage

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock

internal fun mockStorage() = mock<StorageImpl> {
    on { identityKeyPair } doReturn mock()
    on { identityCertificate } doReturn mock()
    on { gatewayCertificate } doReturn mock()
    on { publicThirdParty } doReturn mock()
    on { privateThirdParty } doReturn mock()
}
