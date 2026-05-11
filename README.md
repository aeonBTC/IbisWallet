# Ibis Wallet

A self-custodial Bitcoin wallet with a focus on intutive design, privacy, and modular customizability.

Designed for experienced users - no hand-holding, no training wheels.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

## Key Features

### Layer 1 — Bitcoin
- **Multi-Wallet** - Create, import, export, and switch between multiple wallets
- **Multi-Seed** - Supports BIP39 or Electrum seed phrases for wallet imports
- **Multisig Wallets** - Import multisig descriptors, coordinate PSBT signing, and sign locally as a cosigner
- **Watch-only Wallets** - Import xpub/zpub, output descriptors, or single address
- **Import Private Key** - Sweep or import private keys (WIF format)
- **Hardware Wallet Signing** - Use animated QR codes or .psbt files for air-gapped key signing
- **Coin Control** - Select specific UTXOs, freeze/unfreeze, send from individual outputs
- **RBF & CPFP** - Bump fees on unconfirmed transactions, including multisig-safe PSBT fee bump flows
- **Cancel Transactions** - Cancel unconfirmed outgoing transactions with RBF
- **Manual Broadcast** - Broadcast any signed raw transaction directly to the Bitcoin network
- **Batch Sending** - Send to multiple recipients in a single transaction
- **Silent Payments** - Send to BIP-352 Silent Payment addresses
- **Message Signing** - Sign and verify messages with BIP137
- **BIP329 Labels** - Industry-standard wallet labels for transactions and addresses
- **Transaction Search** - Search transaction history by date, amount, address, or label
- **Built on** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Liquid & Spark (Ark coming soon)
- **Modular Integration** - Choose which Layer 2 to enable for each wallet
- **Liquid** - Full Liquid wallet with doconfidential transactions (Built on [LWK](https://github.com/Blockstream/lwk))
- **Spark** - Spark wallet integration with Lightning and on-chain send and receive (Built on [Breez-SDK](https://github.com/breez/spark-sdk))
- **Lightning Payments** - Pay Bolt 11 and Bolt 12 invoices, or Lightning addresses
- **Lightning Invoices** - Generate Bolt 11 invoices
- **Watch-only Liquid Wallets** - Import Liquid watch-only wallets using SLIP77 descriptors
- **Liquid USDt** - Hold and transact USDt on Liquid
- **Chain Swaps** - Easily swap between L1 and L2
- **Coin Control** - Select specific UTXOs for swaps and payments
- **BIP329 Labels** - Label support for Liquid and Spark transactions


### Privacy & Security
- **Offline by Default** - App launches with zero external connections
- **Built-in Tor** - Native Tor integration, no need for Orbot or external proxies
- **PIN & Biometrics** - With configurable lock timing
- **Privacy Toggle** - Hide all wallet amounts and balances
- **Wallet Locks** - Lock specific wallets independently
- **Duress PIN** - Configure a duress PIN that opens a decoy wallet
- **Auto-Wipe** - Set a threshold for failed unlocks that automatically and irreversibly wipes all app data
- **Cloak Mode** - Disguise Ibis as a calculator app
- **Hardened Metadata** - Sanitized logs and UI errors for reduced OS-visible metadata

### Connectivity & Servers
- **Custom Servers** - Connect to your own Electrum, block explorer, and fee estimation servers
- **NFC Support** - Broadcast and receive payment requests via NFC tap
- **Bitcoin URI Handling** - Register as a handler for `bitcoin:` links

### Localization
- **Languages** - English, Russian, Spanish, and Portuguese (Brazil)

### Backup & Restore
- **Full Encrypted Backups** - Backup and restore the entire app state including wallets, settings, and labels

## Building

Requires Android Studio with JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Tests
./gradlew jacocoUnitTestReport    # Generate coverage report
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 | **ARM only** (armeabi-v7a, arm64-v8a)

## Bug Bounty

Offering $1000 paid in BTC to any major vulnerabilities found in Ibis Wallet.

In-scope vulnerabilities are those with a realistic path to loss of funds for our users. Lower-severity issues (UI bugs, rate limiting, etc.) may be acknowledged but are generally out of scope for monetary rewards.

## Disclaimer

Ibis is coded and audited by the most current frontier AI models.

## License

Open source. See [LICENSE](LICENSE) for details.
