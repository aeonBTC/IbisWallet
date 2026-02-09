# Ibis Wallet

A self-custody Bitcoin wallet for Android, inspired by [Sparrow Wallet](https://sparrowwallet.com/) but built for mobile. Designed for experienced users — no hand-holding, no training wheels.

Built with [BDK](https://bitcoindevkit.org/).

## Key Features

- **Multiple address types** — Legacy, Native SegWit, Taproot
- **Multi-wallet** — create, import, and switch between wallets
- **Watch-only wallets** — import xpub/zpub, output descriptors, ColdCard JSON, Specter JSON, or BC-UR QR codes
- **Hardware wallet signing** — animated BC-UR QR codes for PSBTs, compatible with SeedSigner, ColdCard, Keystone, Passport, Jade, BitBox02
- **Coin control** — select specific UTXOs, freeze/unfreeze, send from individual outputs
- **Fee control** — Electrum or mempool.space estimation, manual override
- **RBF & CPFP** — bump fees on unconfirmed transactions, both outgoing and incoming
- **Multi-recipient** — batch multiple outputs in a single transaction
- **Built-in Tor** — automatic .onion detection, all traffic routed through Tor when enabled
- **Custom Electrum server** — connect to your own server, QR import, SSL and Tor support
- **Encrypted backups** — AES-256-GCM with optional labels, import/export via file
- **Privacy mode** — tap to hide all balances and amounts across the entire app
- **PIN & biometrics** — configurable lock timing, screenshot prevention
- **BIP21 & QR scanning** — supports SeedQR, CompactSeedQR, animated BC-UR, ColdCard/Specter JSON, plain addresses, server addresses
- **Block explorer** — mempool.space (clearnet or onion) or custom instance

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
