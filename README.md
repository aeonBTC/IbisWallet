# Ibis Wallet

A self-custody Bitcoin wallet for Android, inspired by [Sparrow Wallet](https://sparrowwallet.com/) but built for mobile. Designed for experienced users — no hand-holding, no training wheels.

<img width="200" height="444" alt="Screenshot_20260209_000530" src="https://github.com/user-attachments/assets/7f122a48-7ff4-4670-bab9-275a91564013" />
<img width="200" height="444" alt="Screenshot_20260209_002731" src="https://github.com/user-attachments/assets/a02f2867-e396-42c9-85b8-ce2dfc0438c7" />
<img width="200" height="444" alt="Screenshot_20260209_000059" src="https://github.com/user-attachments/assets/da22755b-f86d-4923-b31d-669997a43e63" />
<img width="200" height="444" alt="Screenshot_20260209_002804" src="https://github.com/user-attachments/assets/8b6ea96c-f569-473c-8b38-3ad7a08d47a4" />

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
