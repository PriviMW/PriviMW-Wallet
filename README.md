# PriviMW Wallet

Native Android wallet for the Beam privacy blockchain — encrypted messaging, group chats, DApp store, and confidential transactions.

## Features

- **Wallet** — Send/receive BEAM and Confidential Assets (offline, online, max privacy)
- **PriviMe Messaging** — End-to-end encrypted chat via SBBS (no servers)
- **Group Chats** — On-chain groups with admin roles, bans, invites
- **DApp Store** — Browse and install Beam DApps
- **Mobile Node** — Optional FlyClient lightweight verification
- **Profile Pictures** — SBBS-based avatar exchange
- **Stickers** — Custom packs with animated TGS support

## Tech Stack

- **Language**: Kotlin + Jetpack Compose
- **Wallet Core**: Beam C++ via JNI (`libwallet-jni.so`)
- **Database**: Room + SQLCipher (encrypted)
- **File Sharing**: IPFS via `libipfs-bindings.so`
- **Network**: Beam Mainnet (SBBS for messaging, BVM for contracts)

## Building

### Prerequisites

- Android Studio Ladybug+
- JDK 17
- Android SDK 34, NDK 27

### Build APK

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
app/src/main/java/com/privimemobile/
├── chat/                  # Messaging system
│   ├── db/                # Room database (entities, DAOs)
│   ├── group/             # Group chat manager + PendingTx
│   ├── identity/          # Handle registration + profile
│   ├── contacts/          # Contact resolution
│   ├── processor/         # SBBS message processing
│   ├── transport/         # SBBS + IPFS transport
│   └── ChatService.kt    # Singleton orchestrator
├── protocol/              # Beam wallet JNI bridge
├── wallet/                # Wallet events, background service
├── ui/                    # Jetpack Compose screens
│   ├── wallet/            # Send, receive, TX detail
│   ├── chat/              # Chat, groups, registration
│   ├── dapps/             # DApp store + WebView
│   ├── settings/          # Settings + mobile node
│   └── navigation/        # Nav graph
└── MainActivity.kt
```

## Contract

The PriviMe smart contract source is in [privimw-dapps](https://github.com/PriviMW/privimw-dapps). The compiled `app.wasm` shader is bundled in `app/src/main/assets/`.

## License

MIT License — see [LICENSE](LICENSE)
