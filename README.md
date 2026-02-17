# Ibis Wallet

A self-custody Bitcoin wallet for Android, inspired by [Sparrow Wallet](https://sparrowwallet.com/) but built for mobile. 

Designed for experienced users - no hand-holding, no training wheels.

<img width="200" height="444" alt="Screenshot_20260209_000530" src="https://github.com/user-attachments/assets/7f122a48-7ff4-4670-bab9-275a91564013" />
<img width="200" height="444" alt="Screenshot_20260209_002731" src="https://github.com/user-attachments/assets/a02f2867-e396-42c9-85b8-ce2dfc0438c7" />
<img width="200" height="444" alt="Screenshot_20260209_000059" src="https://github.com/user-attachments/assets/da22755b-f86d-4923-b31d-669997a43e63" />
<img width="200" height="444" alt="Screenshot_20260209_002804" src="https://github.com/user-attachments/assets/8b6ea96c-f569-473c-8b38-3ad7a08d47a4" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

## Key Features

- **Multi-wallet** — Create, import, export, and switch between multiple wallets
- **Multi-Seed** - Supports BIP39 or Electrum seed phrases for importing
- **Watch-only wallets** — Import xpub/zpub, output descriptors, or single address
- **Import Private Key** - Sweep or import private keys (WIF format)
- **Hardware wallet signing** — Animated QR codes for air gapped PSBTs
- **Built-in Tor** — Native Tor integreation, no need for Orbot or external Tor proxies
- **Custom Electrum server** — Connect to your own server, TCP, SSL, and Tor support
- **Coin control** — Select specific UTXOs, freeze/unfreeze, send from individual outputs
- **RBF & CPFP** — Bump fees on unconfirmed transactions, both outgoing and incoming
- **PIN & biometrics** — With configurable lock timing
- **Duress Pin** - Configure a secondary PIN that opens a decoy wallet
- **Auto-Wipe** - Set a threshold for failed unlock that automatically and irreversibly wipes all app data
- **Cloak Mode** - Disguise Ibis as a calculator app
- **Manual Broadcast Raw Transactions** - Broadcast any signed transaction directly to the Bitcoin network
- **Multi-recipient** — Batch multiple outputs in a single transaction
- **Encrypted backups** — AES-256 encryption with optional label and custom server backup, import/export via file
- **Built with** [BDK](https://bitcoindevkit.org/).

## Building

Requires Android Studio with JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Tests
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 | **ARM only** (armeabi-v7a, arm64-v8a)

## License

Open source. See [LICENSE](LICENSE) for details.
