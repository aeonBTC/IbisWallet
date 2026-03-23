# Ibis Wallet

A self-custody Bitcoin wallet for Android, inspired by [Sparrow Wallet](https://sparrowwallet.com/) but built for mobile. 

Ibis gives you complete control over your wallet and is designed for experienced users - no hand-holding, no training wheels.

<img width="1700" height="841" alt="1" src="https://github.com/user-attachments/assets/839e9230-0280-4f16-84cc-5525ad9cbef9" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

## Key Features

### Layer 1 — Bitcoin
- **Multi-Wallet** - Create, import, export, and switch between multiple wallets
- **Multi-Seed** - Supports BIP39 or Electrum seed phrases for importing
- **Watch-only Wallets** - Import xpub/zpub, output descriptors, or single address
- **Import Private Key** - Sweep or import private keys (WIF format)
- **Hardware Wallet Signing** - Use animated QR codes or .psbt files for air-gapped key signing
- **Coin Control** - Select specific UTXOs, freeze/unfreeze, send from individual outputs
- **RBF & CPFP** - Bump fees on unconfirmed transactions, both outgoing and incoming
- **Cancel Transactions** - Cancel unconfirmed outgoing transactions with RBF
- **Manual Broadcast** - Broadcast any signed raw transaction directly to the Bitcoin network
- **Batch Sending** - Send to multiple recipients in a single transaction
- **BIP329 Labels** - Industry-standard wallet labels for transactions and addresses
- **Transaction Search** - Search transaction history by date, amount, or label
- **Built with** - [BDK](https://bitcoindevkit.org/)

### Layer 2 — Liquid Network (Ark coming soon)
- **Modular Integration** - Enable or disable Liquid for each wallet
- **Send & Receive L-BTC** - Full Liquid wallet with confidential transactions
- **Lightning Payments** - Pay Bolt 11 and Bolt 12 invoices via Boltz submarine swaps
- **Lightning Invoices** - Generate Bolt 11 invoices via Boltz reverse swaps
- **Chain Swaps** - BTC ↔ L-BTC atomic swaps via Boltz and SideSwap
- **Coin Control** - Select specific Liquid UTXOs for swaps and Lightning payments
- **BIP329 Labels** - Label support for Liquid transactions
- **Built with** - [LWK](https://github.com/Blockstream/lwk)

### Privacy & Security
- **Built-in Tor** - Native Tor integration, no need for Orbot or external proxies
- **PIN & Biometrics** - With configurable lock timing
- **Individual Wallet Lock** - Lock specific wallets independently
- **Duress PIN** - Configure a secondary PIN that opens a decoy wallet
- **Auto-Wipe** - Set a threshold for failed unlock that automatically and irreversibly wipes all app data
- **Cloak Mode** - Disguise Ibis as a calculator app
- **Offline Mode** - Electrum servers and external services are disabled by default

### Connectivity & Servers
- **Custom Servers** - Connect to your own Electrum, block explorer, and fee estimation servers
- **NFC Support** - Broadcast and receive payment requests via NFC tap
- **Bitcoin URI Handling** - Register as a handler for `bitcoin:` links

### Backup & Restore
- **Full App Backup** - Backup and restore the entire app state including wallets, settings, and labels
- **Encrypted Backups** - Back up wallets with AES-256 encryption 

## Building

Requires Android Studio with JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Tests
./gradlew jacocoUnitTestReport    # Generate coverage report
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 | **ARM only** (armeabi-v7a, arm64-v8a)

## License

Open source. See [LICENSE](LICENSE) for details.
