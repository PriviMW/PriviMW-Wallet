# PriviMW — Wallet on the Beam Privacy Blockchain

Native Android wallet with encrypted messaging, DApp store, and confidential transactions.

Built with Kotlin + Jetpack Compose. No React Native.

## Build

Open in Android Studio and build `app` module, or:

```bash
./gradlew assembleDebug
```

## Architecture

- **JNI Core**: C++ wallet engine (libwallet-jni.so) — Beam MimbleWimble protocol
- **Wallet Manager**: Kotlin singleton managing wallet lifecycle
- **Event Bus**: SharedFlow-based event delivery from JNI callbacks
- **UI**: Jetpack Compose with Material 3
- **DApp Store**: WebView-based DApp runtime with native TX consent dialog

## License

MIT
