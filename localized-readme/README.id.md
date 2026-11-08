# Ibis Wallet

Ibis adalah dompet Bitcoin modular dengan sistem self-custodial (pengguna memegang kendali penuh atas aset dan kunci privatnya), yang berfokus pada desain intuitif, privasi, dan kemampuan penyesuaian.

Dirancang untuk pengguna berpengalaman - tanpa panduan berlebihan atau fitur yang terlalu membatasi pengguna.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Bahasa yang tersedia:** Inggris · [Spanyol](localized-readme/README.es.md) · [Rusia](localized-readme/README.ru.md) · [Portugis (Brasil)](localized-readme/README.pt-BR.md) · [Perancis](localized-readme/README.fr.md) · [Jerman](localized-readme/README.de.md) · [Jepang](localized-readme/README.ja.md) · [Mandarin](localized-readme/README.zh-CN.md) · [Indonesian](localized-readme/README.id.md)

## Fitur Utama

### Layer 1 — Bitcoin
- **Multi-Wallet** - Membuat, mengimpor, mengekspor, dan berpindah di antara beberapa wallet
- **Multi-Seed** - Mendukung frasa seed BIP39 atau Electrum untuk mengimpor wallet
- **Dice Entropy** - Menghasilkan seed melalui lemparan dadu
- **Multisig Wallets** - Mengimpor descriptor multisig, mengoordinasikan penandatanganan PSBT, dan menandatangani transaksi secara lokal sebagai salah satu cosigner
- **Watch-only Wallets** - Mengimpor xpub/zpub, output descriptor, atau satu alamat Bitcoin.
- **Import Private Key** - Melakukan sweep atau mengimpor private key dalam format WIF
- **Silent Payments** - Dukungan penuh BIP-352 untuk mengirim dan menerima pembayaran Silent Payments, dengan Frigate sebagai server bawaan
- **Hardware Wallet Signing** - Menggunakan kode QR animasi atau file .psbt untuk menandatangani transaksi menggunakan hardware wallet secara offline (air-gapped)
- **NFC PSBT Signing** - Mengirim PSBT ke hardware wallet melalui sentuhan NFC dan menerima kembali tanda tangannya dengan cara yang sama
- **Offline Signer** - Menjalankan Ibis pada ponsel offline sebagai perangkat penandatanganan cold storage untuk PSBT dari watch-only wallet.
- **Dust Protection** - Menandai transaksi atau output yang dicurigai sebagai dust dan mencegahnya masuk ke dalam pemilihan koin otomatis
- **Coin Control** - Memilih UTXO tertentu, membekukan atau mencairkannya, serta mengirim Bitcoin dari output tertentu
- **Require Coin Control** - Opsi untuk mewajibkan pemilihan UTXO secara manual sebelum mengirim transaksi on-chain
- **RBF & CPFP** - Menaikkan biaya transaksi yang belum terkonfirmasi, termasuk alur peningkatan biaya melalui PSBT yang kompatibel dengan multisig
- **RBF by Default** - Transaksi on-chain secara default mengaktifkan sinyal Replace-By-Fee (RBF), tetapi fitur ini dapat dinonaktifkan
- **Cancel Transactions** - Membatalkan transaksi keluar yang belum terkonfirmasi menggunakan RBF
- **Manual Broadcast** - Menyiarkan transaksi mentah (raw transaction) yang sudah ditandatangani secara langsung ke jaringan Bitcoin
- **Batch Sending** - Mengirim Bitcoin kepada beberapa penerima dalam satu transaksi
- **Message Signing** - Menandatangani dan memverifikasi pesan menggunakan BIP137
- **Checksum Helper** - Menghitung kata checksum yang hilang dari awalan seed 11 atau 23 kata
- **BIP329 Labels** - Menggunakan standar label wallet untuk transaksi dan alamat
- **Transaction Search** - Mencari riwayat transaksi berdasarkan tanggal, alamat, atau label
- **Dibangun menggunakan** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Native Lightning, Ark, Spark, dan Liquid
- **Modular Integration** - Memilih Layer 2 mana yang ingin diaktifkan untuk setiap wallet
- **Lightning** - Terhubung ke node jarak jauh melalui LND (LND REST), CLN (clnrest), atau NWC (NIP-47)
- **Ark** - Mendukung Ark Layer 2 dengan pengelolaan VTXO, pengiriman dan penerimaan Lightning, boarding dalam satu transaksi, serta unilateral exit (keluar secara mandiri) (Dibangun menggunakan [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Mendukung Spark Layer 2 dengan pengiriman dan penerimaan Lightning maupun on-chain, serta unilateral exit (Dibangun menggunakan [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - FWallet Liquid lengkap dengan transaksi rahasia (confidential transactions) dan dukungan USDt (Dibangun menggunakan [LWK](https://github.com/Blockstream/lwk))
- **Watch-only Liquid Wallets** - Mengimpor wallet Liquid watch-only menggunakan descriptor SLIP77
- **Lightning Payments** - Membayar invoice Bolt 11 dan Bolt 12, atau menggunakan Lightning Address
- **Lightning Invoices** - Membuat invoice Bolt 11
- **Chain Swaps** - Melakukan pertukaran dengan mudah antara Layer 1 dan Layer 2
- **Coin Control** - Memilih UTXO tertentu untuk proses swap dan pembayaran
- **BIP329 Labels** - Mendukung penggunaan label BIP329 untuk transaksi Ark, Spark, dan Liquid

### Privasi & Keamanan
- **Offline by Default** - Aplikasi dijalankan tanpa koneksi eksternal secara default
- **Built-in Tor** - Integrasi Tor bawaan, sehingga tidak membutuhkan Orbot atau proxy eksternal
- **PIN & Biometrics** - Menggunakan PIN dan biometrik dengan pengaturan waktu penguncian yang dapat disesuaikan
- **Duress PIN** - Mengatur PIN khusus yang ketika digunakan akan membuka wallet umpan (decoy wallet)
- **Wipe PIN** - Mengatur PIN sekunder yang secara diam-diam menghapus seluruh data wallet pada perangkat
- **Auto-Wipe** - Mengatur batas jumlah kegagalan pembukaan kunci yang akan memicu penghapusan seluruh data aplikasi secara otomatis dan permanen
- **Clear Clipboard** - Opsi untuk menghapus isi clipboard secara otomatis ketika aplikasi terkunci atau ditutup
- **Cloak Mode** - Menyamarkan tampilan Ibis sebagai aplikasi kalkulator
- **Privacy Toggle** - Menyembunyikan seluruh nominal dan saldo wallet
- **Wipe History** - Menghapus riwayat transaksi wallet tertentu atau seluruh riwayat secara lokal
- **Wallet Locks** - Mengunci setiap wallet secara terpisah
- **Hardened Metadata** - Membersihkan log dan pesan kesalahan antarmuka untuk mengurangi metadata yang dapat terlihat oleh sistem operasi

### Konektivitas & Server
- **Custom Servers** - Terhubung ke server Electrum, block explorer, dan server estimasi biaya transaksi milik sendiri
- **NFC Support** - Mengirim dan menerima permintaan pembayaran melalui sentuhan NFC
- **Update Notifications** - Mengaktifkan atau menonaktifkan notifikasi ketika versi baru tersedia
- **Bitcoin URI Handling** - Mendaftarkan Ibis sebagai aplikasi penangan tautan `bitcoin:`

### Lokalisasi
- **Bahasa** - Inggris, Spanyol, Rusia, Portugis (Brasil), Prancis, Jerman, Jepang, Mandarin Sederhana dan Indonesian
- **Typeface** - Menyediakan beberapa pilihan pengaturan jenis huruf

### Backup & Restore
- **Full Encrypted Backups** - Mencadangkan dan memulihkan seluruh kondisi aplikasi, termasuk wallet, pengaturan, dan label, dalam bentuk backup terenkripsi

## Build (Membangun Aplikasi)

Membutuhkan Android Studio dengan JDK 17.

```bash
./gradlew :app:assembleDebug      # Build versi Debug
./gradlew :app:assembleRelease    # Build versi Release
./gradlew testDebugUnitTest       # Menjalankan pengujian unit Debug
./gradlew jacocoUnitTestReport    # Membuat laporan cakupan pengujian
```

**Minimum SDK:** 26 (Android 8.0) | **Target SDK:** 36 | **Arsitektur ARM:** (armeabi-v7a, arm64-v8a)

## Bug Bounty (Program Imbalan Penemuan Bug)

> [!IMPORTANT]
> **Program bug bounty saat ini ditangguhkan karena keterbatasan dana.** Laporan kerentanan tetap diterima dan akan mendapatkan tanggapan, tetapi tidak ada imbalan uang yang akan dibayarkan sampai program tersebut kembali dibuka.

Pengembang menawarkan imbalan sebesar $1.000 dalam BTC untuk setiap kerentanan kritis pada Ibis Wallet yang dapat menyebabkan hilangnya dana pengguna tanpa izin.

Dalam lingkup bounty terdapat kerentanan yang menyediakan jalur serangan yang praktis dan realistis yang mengakibatkan pencurian atau kehilangan dana permanen bagi pengguna (misalnya Pengambilalihan wallet dari jarak jauh, ekstraksi seed phrase, manipulasi transaksi, dll.).

Ibis adalah perangkat lunak beta dan dibuat untuk pengguna Bitcoin berpengalaman. Cadangan seed di luar perangkat diharapkan menjadi praktik standar: masalah yang dapat dipulihkan sepenuhnya dengan memulihkan dari seed, tanpa pencurian pihak ketiga, diakui tetapi di luar cakupan untuk mendapatkan hadiah.

Masalah tingkat keparahan yang lebih rendah, kerentanan teoretis tanpa jalur eksploitasi praktis, atau temuan yang tidak menyebabkan kerugian dana akan diakui tetapi umumnya di luar cakupan untuk imbalan finansial.

Penghargaan Bug Bounty: [15 Agustus 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Disclaimer (Pernyataan Penyangkalan)

Ibis dikembangkan dan diaudit menggunakan model AI terdepan yang tersedia saat ini.

Audit: [29 Oktober 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v5.1.0-beta) · [15 Agustus 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Donasi

Karena awalnya saya membuat dompet ini untuk diri saya sendiri, saya tidak mengharapkan donasi. Namun, token tidak murah. Jadi jika Anda ingin menunjukkan terima kasih, silakan.

<img width="217" height="200" alt="image" src="https://github.com/user-attachments/assets/ea7993d0-ce33-4578-b534-5ea86cd74038" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```


## Lisensi

Bersifat Open source. Lihat [LICENSE](LICENSE) untuk informasi lebih lanjut.
