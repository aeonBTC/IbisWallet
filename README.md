# Ibis Wallet

A self-custody Bitcoin wallet for Android built with [BDK](https://bitcoindevkit.org/). Designed for experienced users who value privacy and control.

## Features

### Wallet
- Generate new wallets with BIP39 CSPRNG entropy (12 or 24 words)
- Import existing wallets via seed phrase, extended public key, or encrypted backup file
- Multi-wallet support with optional BIP39 passphrase
- Watch-only wallets via extended public key (xpub/zpub/)
- Legacy (P2PKH), Native SegWit (P2WPKH), and Taproot (P2TR) address types
- Custom derivation paths

### Send
- QR code scanning with BIP21 URI parsing
- Amount input in BTC, sats, or USD with live conversion
- Custom fee rates with mempool.space fee estimation
- Coin control (select specific UTXOs)
- Transaction labels
- RBF fee bumping and CPFP for unconfirmed transactions

### PSBT / Hardware Wallet Signing
- Create unsigned PSBTs from watch-only wallets for external signing
- Export PSBTs as animated BC-UR QR codes (`ur:crypto-psbt`) for hardware wallets
- Scan signed PSBTs or raw transactions back via animated multi-frame QR scanner
- Compatible with Keystone, Foundation Passport, SeedSigner, Jade, ColdCard, BitBox02, and other BC-UR capable devices
- Also accepts single-frame base64 PSBT and raw transaction hex
- Copy unsigned PSBT to clipboard for file-based signing workflows

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
- **Hummingbird** 1.7.2 — BC-UR encoding/decoding for animated PSBT QR codes
- **OkHttp** — HTTP client for fee/price APIs
- **EncryptedSharedPreferences** — Secure local storage

## License

Open source. See [LICENSE](LICENSE) for details.
