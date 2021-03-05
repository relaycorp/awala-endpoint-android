package tech.relaycorp.relaydroid

// Temporary solution for the global val access issue
public object RelaynetTemp {
    public val GatewayClient: GatewayClientImpl get() = Relaynet.gatewayClientImpl
}
