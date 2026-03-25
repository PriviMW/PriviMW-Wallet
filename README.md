# PriviMW Wallet

Native Android wallet for the [Beam](https://beam.mw) privacy blockchain — encrypted messaging, group chats, DApp store, and confidential transactions.

**[Download Latest APK](https://github.com/PriviMW/PriviMW-Wallet/releases/latest)**

## Features

- **Wallet** — Send/receive BEAM and Confidential Assets (offline, online, max privacy)
- **PriviMe Messaging** — End-to-end encrypted chat via SBBS (no servers, no intermediaries)
- **Group Chats** — On-chain groups with admin roles, bans, invites, password-protected private groups
- **DApp Store** — Browse and install Beam DApps
- **Mobile Node** — Optional FlyClient lightweight verification for trustless operation
- **Profile Pictures** — Peer-to-peer avatar exchange via SBBS
- **Stickers** — Custom packs with animated TGS/Lottie support
- **Tips** — Send BEAM/assets directly in chat conversations
- **Polls** — Create and vote on polls in DM and group chats

## Privacy & Security

- **No servers** — All messaging is peer-to-peer via Beam's SBBS (Secure Bulletin Board System)
- **End-to-end encrypted** — Messages encrypted per-recipient using public key cryptography
- **Encrypted local storage** — SQLCipher database with Android Keystore-backed encryption
- **No tracking** — No analytics, no telemetry, no cloud backups
- **Brute-force protection** — Exponential lockout on failed password attempts
- **Transaction auth** — Password/biometric required for all fund transfers
- **On-chain privacy** — All wallet transactions use Beam's MimbleWimble confidential amounts
- **Open source** — Full source code available for audit

### What stays on your device

- Chat messages, media, stickers
- Profile pictures
- Wallet database and keys
- App settings and preferences

### What goes on-chain

- Handle registration (public key + encrypted SBBS address)
- Group membership records (handle + role)
- Group metadata (name, settings — not message content)
- Wallet transactions (confidential amounts via MimbleWimble)

**Message content is NEVER stored on-chain.** All messages are transmitted via SBBS and stored locally.

## Tech Stack

- **Language**: Kotlin + Jetpack Compose
- **Wallet Core**: Beam C++ via JNI (`libwallet-jni.so`)
- **Database**: Room + SQLCipher (encrypted)
- **Network**: Beam Mainnet (SBBS for messaging, BVM for contracts)
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)

## Building

### Prerequisites

- Android Studio Ladybug+
- JDK 17
- Android SDK 34, NDK 27

### Build

```bash
./gradlew assembleDebug
```

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
│   ├── transport/         # SBBS transport layer
│   └── ChatService.kt    # Singleton orchestrator
├── protocol/              # Beam wallet JNI bridge
├── wallet/                # Wallet events, background service
├── dapp/                  # DApp WebView bridge
├── ui/                    # Jetpack Compose screens
│   ├── wallet/            # Send, receive, TX history
│   ├── chat/              # Chat, groups, registration
│   ├── dapps/             # DApp store + viewer
│   ├── settings/          # Settings, mobile node, about
│   └── navigation/        # Nav graph
└── MainActivity.kt
```

## Native Libraries

The pre-compiled native libraries are built from our open-source Beam forks:

| Library | Source | Description |
|---------|--------|-------------|
| `libwallet-jni.so` | [PriviMW/beam](https://github.com/PriviMW/beam) | Beam C++ wallet core (MimbleWimble, SBBS, BVM) |
| `libipfs-bindings.so` | [PriviMW/asio-ipfs](https://github.com/PriviMW/asio-ipfs) | IPFS transport bindings |

Forked from the official [BeamMW/beam](https://github.com/BeamMW/beam) with minimal changes for mobile compatibility.

## Smart Contracts

The PriviMe and PriviBets smart contract source code is in [privimw-dapps](https://github.com/PriviMW/privimw-dapps). The compiled `app.wasm` shader is bundled in `app/src/main/assets/`.

## Ecosystem

- [Beam](https://beam.mw) — Privacy blockchain
- [Beam Explorer](https://explorer.beam.mw) — Block explorer
- [Beam Documentation](https://documentation.beam.mw) — Developer docs
- [PriviMW DApps](https://github.com/PriviMW/privimw-dapps) — Smart contracts
- [PriviMW Beam Fork](https://github.com/PriviMW/beam) — C++ wallet core source
- [PriviMW IPFS Fork](https://github.com/PriviMW/asio-ipfs) — IPFS bindings source

## Donate

Support PriviMW development (BEAM):

```
1f80a21cf2b4baed9f88ede28bb05aa6a6032987cc3cc3934bb9e06d4d9fdc4feb3
```

## License

Apache License 2.0 — see [LICENSE](LICENSE)
