# awaladroid: High-level library for Android apps implementing Awala endpoints

Please refer to the [Android codelabs](https://codelabs.awala.network/?cat=android) to learn how to use this library. The reference documentation is available on [docs.relaycorp.tech](https://docs.relaycorp.tech/awala-endpoint-android/).

## Install

[Due to a bug in Android](https://issuetracker.google.com/issues/159151549), you'll have to exclude Bouncy Castle from Jetifier transformations by adding the following to `gradle.properties`:

```
# Workaround for https://issuetracker.google.com/issues/159151549
android.jetifier.blacklist = bcprov-jdk15on-1.*.jar
```
