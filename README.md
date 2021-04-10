# awaladroid: High-level library for Android apps implementing Awala endpoints

Please refer to the [Android codelabs](https://codelabs.awala.network/?cat=android) to learn how to use this library. The reference documentation is available on [docs.relaycorp.tech](https://docs.relaycorp.tech/awala-endpoint-android/).

## Install

[Get the latest version from JitPack](https://jitpack.io/#relaycorp/awala-endpoint-android). We'll release it to Maven Central [eventually](https://github.com/relaycorp/awala-endpoint-android/issues/80).

[Due to a bug in Android](https://issuetracker.google.com/issues/159151549), you'll also have to exclude Bouncy Castle from Jetifier transformations by adding the following to `gradle.properties`:

```
# Workaround for https://issuetracker.google.com/issues/159151549
android.jetifier.blacklist = bcprov-jdk15on-1.*.jar
```

## Security and privacy considerations

The items below summarize the security and privacy considerations specific to this app. For a more general overview of the security considerations in Awala, please refer to [RS-019](https://specs.awala.network/RS-019).

### No encryption at rest on Android 5

We use the [Android Keystore system](https://developer.android.com/training/articles/keystore) to protect sensitive cryptographic material, such as long-term and ephemeral keys. Unfortunately, [Android 5 doesn't actually encrypt anything at rest](https://github.com/relaycorp/relaynet-gateway-android/issues/247).

### External communication

This library exclusively communicates with the private gateway installed on the device. It does not communicate with other apps or any Internet host.

### Android permissions

This library needs the following Android permissions:

- `android.permission.INTERNET`: To be able to communicate with the private gateway on `127.0.0.1:13276`. **This library does not communicate with the Internet**.
- `tech.relaycorp.gateway.SYNC`: To be able to bind to the private gateway.

They will be automatically added on your behalf, so you don't need to include them in your `AndroidManifest.xml`.

## Development

The project requires [Android Studio](https://developer.android.com/studio/) 4+.

## Contributing

We love contributions! If you haven't contributed to a Relaycorp project before, please take a minute to [read our guidelines](https://github.com/relaycorp/.github/blob/master/CONTRIBUTING.md) first.
