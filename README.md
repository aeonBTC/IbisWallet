# Ibis Wallet

A self-custodial Bitcoin wallet with a focus on intutive design, privacy, and modular customizability.

Designed for experienced users - no hand-holding, no training wheels.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

## Key Features

### Layer 1 — Bitcoin
- **Multi-Wallet** - Create, import, export, and switch between multiple wallets
- **Multi-Seed** - Supports BIP39 or Electrum seed phrases for wallet imports
- **Watch-only Wallets** - Import xpub/zpub, output descriptors, or single address
- **Import Private Key** - Sweep or import private keys (WIF format)
- **Hardware Wallet Signing** - Use animated QR codes or .psbt files for air-gapped key signing
- **Coin Control** - Select specific UTXOs, freeze/unfreeze, send from individual outputs
- **RBF & CPFP** - Bump fees on unconfirmed transactions, both outgoing and incoming
- **Cancel Transactions** - Cancel unconfirmed outgoing transactions with RBF
- **Manual Broadcast** - Broadcast any signed raw transaction directly to the Bitcoin network
- **Batch Sending** - Send to multiple recipients in a single transaction
- **BIP329 Labels** - Industry-standard wallet labels for transactions and addresses
- **Transaction Search** - Search transaction history by date, amount, address, or label
- **Built on** [BDK](https://github.com/bitcoindevkit/)

### Layer 2 — Liquid Network (Ark coming soon)
- **Modular Integration** - Enable or disable Liquid for each wallet
- **Send & Receive L-BTC** - Full Liquid wallet with confidential transactions
- **Lightning Payments** - Pay Bolt 11 and Bolt 12 invoices, or Lightning addresses via Boltz submarine swaps
- **Lightning Invoices** - Generate Bolt 11 invoices via Boltz reverse swaps
- **Watch-only Liquid Wallets** - Import Liquid watch-only wallets using SLIP77 descriptors
- **Liquid USDt** - Hold and transact USDt on Liquid
- **Chain Swaps** - BTC ↔ L-BTC atomic swaps via Boltz or SideSwap
- **Coin Control** - Select specific UTXOs for swaps and Lightning payments
- **BIP329 Labels** - Label support for Liquid transactions
- **Built on** [LWK](https://github.com/Blockstream/lwk)

### Privacy & Security
- **Default Offline** - App launches with zero external connections
- **Built-in Tor** - Native Tor integration, no need for Orbot or external proxies
- **PIN & Biometrics** - With configurable lock timing
- **Privacy Toggle** - Hide all wallet amounts and balances
- **Wallet Locks** - Lock specific wallets independently
- **Duress PIN** - Configure a duress PIN that opens a decoy wallet
- **Auto-Wipe** - Set a threshold for failed unlocks that automatically and irreversibly wipes all app data
- **Cloak Mode** - Disguise Ibis as a calculator app

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

## Bug Bounty
Offering $1000 paid in BTC for any major bugs found in app

## Disclaimer
Ibis is laregely vibecoded and is audited by the most current frontier AI models.

## License
Open source. See [LICENSE](LICENSE) for details.
