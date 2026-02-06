# Ibis Wallet

A self-custody Bitcoin wallet for Android built with [BDK](https://bitcoindevkit.org/ and designed for experienced users who value privacy and control.

## Features

### Wallet
- Multi-wallet support with BIP39 seed phrases (12–24 words) and optional passphrase
- Watch-only wallets via extended public key (xpub/zpub)
- Legacy (P2PKH), Native SegWit (P2WPKH), and Taproot (P2TR) address types
- Custom derivation paths

### Send
- QR code scanning with BIP21 URI parsing
- Amount input in BTC, sats, or USD with live conversion
- Custom fee rates with mempool.space fee estimation
- Coin control (select specific UTXOs)
- Transaction labels
- RBF fee bumping and CPFP for unconfirmed transactions

### Receive
- QR code generation with optional BIP21 amount encoding
- Address labeling
- Automatic unused address generation

### Privacy & Security
- Built-in Tor with automatic .onion detection
- PIN lock and biometric authentication with configurable lock timing
- AES-256-GCM encrypted storage via Android Keystore
- Privacy mode to hide all balances
- Encrypted wallet backup/restore (AES-256-GCM, PBKDF2 210k iterations)

### Server
- Custom Electrum server configuration
- QR code import for server details
- Automatic Tor routing for .onion servers

### Other
- UTXO management with freeze/unfreeze
- Full address list with balances and transaction counts
- Transaction search and filtering
- Block explorer integration (mempool.space clearnet/onion or custom)
- BTC/USD price from mempool.space or CoinGecko

## Building

Requires Android Studio with JDK 17.

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build
./gradlew :app:assembleRelease

# Run tests
./gradlew testDebugUnitTest
```

**Min SDK:** 26 (Android 8.0) &bull; **Target SDK:** 35 &bull; **ARM only** (armeabi-v7a, arm64-v8a)

## Tech Stack

- **BDK** 1.0.0-beta.6 — Bitcoin wallet operations
- **Jetpack Compose** + Material 3 — UI
- **tor-android** 0.4.8.16 — Built-in Tor
- **ZXing** — QR code generation and scanning
- **OkHttp** — HTTP client for fee/price APIs
- **EncryptedSharedPreferences** — Secure local storage

## License

Open source. See [LICENSE](LICENSE) for details.
