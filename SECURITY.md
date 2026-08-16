# Security Policy

Ibis Wallet is a self-custodial Bitcoin wallet. Users hold their own keys. This document describes how secrets are protected, which defenses exist, how to report vulnerabilities, and what is (and is not) in scope.

## Supported Versions

Security fixes are applied to the latest release on [GitHub Releases](https://github.com/aeonBTC/IbisWallet/releases). Older builds are not backported unless noted in a release.

| Version | Supported |
|---------|-----------|
| Latest release | Yes |
| Older releases | No |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security-sensitive findings.**

### Email me

Send reports to **[aeonbtc@proton.me](mailto:aeonbtc@proton.me)**.

- Encrypt with PGP when the finding is sensitive: fingerprint `9A61 2482 4FCC 0EF0 5F3A 8184 C0F0 581E B6C4 E1D6` ([key](https://keys.openpgp.org/vks/v1/by-fingerprint/9A6124824FCC0EF05F3A8184C0F0581EB6C4E1D6)).
- Include: affected version/build, reproduction steps, impact (funds, privacy, lockout, wipe), and any PoC that does **not** put third-party funds at risk.
- You will get a reply when the report is received.

Alternatively, open a [GitHub Security Advisory](https://github.com/aeonBTC/IbisWallet/security/advisories/new) (private) when the repository enables them.

### Bug bounty

A bounty of **$1,000 BTC** is offered for **critical** vulnerabilities with a practical path to **unauthorized loss of user funds** (for example remote wallet compromise, seed extraction, or transaction manipulation that steals or permanently destroys funds).

| In scope for bounty | Out of scope for bounty (may still be acknowledged) |
|---------------------|-----------------------------------------------------|
| Practical theft or permanent loss of funds | Theoretical issues without a realistic exploit path |
| Remote compromise leading to fund loss | UI/UX polish, cosmetic bugs |
| Seed / spend-secret extraction usable offline | Issues requiring physical access + unlocked device + no app lock, without additional privilege escalation |
| Transaction manipulation that steals funds | Issues in third-party servers the user chose (Electrum, explorers, ASP, etc.) unless the client mishandles them in a fund-loss way |
| | Dependency CVEs with no practical impact on Ibis |
| | Social engineering, phishing, or user error |
| | Compromised Android OS / rooted device / malware with full device control (baseline threat; see Threat model) |

Lower-severity issues that do not lead to fund loss may be fixed without a monetary reward.

### Disclosure expectations

- Allow reasonable time for a fix before public disclosure.
- Do not exploit findings against third-party users or mainnet funds you do not control.
- Coordinated disclosure is preferred.

---

## Security model (summary)

| Layer | Approach |
|-------|----------|
| Custody | Self-custodial; keys stay on device |
| At rest | EncryptedSharedPreferences + optional session spend-secret wrapping while app lock is enabled |
| App access | Optional PIN and/or biometric lock |
| Coercion | Duress PIN → decoy wallet; optional Wipe PIN; optional auto-wipe |
| Privacy UI | Cloak Mode (calculator disguise); amount privacy toggle |
| Network | No auto-connect until the user selects services; optional built-in Tor; Electrum SSL TOFU |
| OS backup | Disabled; cloud and device-transfer extraction excluded |
| Screen | `FLAG_SECURE` by default; tapjacking filter |
| Logs | Prefer `SecureLog`; release ProGuard strips verbose/debug/info `Log` calls |
| Release | R8 minify + resource shrink; native UniFFI/JNA bindings kept |

Ibis targets **experienced Bitcoin users**. Features such as cleartext Electrum, custom servers, and remote Lightning credentials assume the user understands the tradeoffs.

---

## Secret storage

### Encrypted preferences

Sensitive configuration and secrets live primarily in **AndroidX `EncryptedSharedPreferences`** (`ibis_secure_prefs`):

- Master key: Android Keystore via `MasterKey` (`AES256_GCM`), alias `_androidx_security_master_key_`
- Pref keys: `AES256_SIV`
- Pref values: `AES256_GCM`

Non-secret settings may use ordinary `SharedPreferences` (`ibis_prefs`). Legacy values found only in regular prefs are migrated into encrypted prefs when read.

If the Keystore master key is missing/invalid while the encrypted prefs file remains (e.g. partial wipe residue), Ibis deletes the stale prefs file and recreates empty encrypted storage.

### Spend secrets (session-wrapped)

While **app lock is enabled** (PIN or biometric), high-value secrets are stored as **spend secrets**:

1. A random **32-byte session master key** is held in memory only while unlocked.
2. Each secret is encrypted with **AES-256-GCM** under that master key (`enc:v1:` prefix + Base64(IV ‖ ciphertext)).
3. The master key is **wrapped** for unlock methods:
   - **PIN / duress PIN**: PBKDF2-HMAC-SHA256 (150 000 iterations, 16-byte random salt) → wrapping key → AES-GCM wrap
   - **Biometric**: Android Keystore AES key with `setUserAuthenticationRequired(true)`, wrap via `BiometricPrompt.CryptoObject`

**Spend-secret keys today:** BIP39 mnemonic, WIF private key, BIP39 passphrase, multisig local cosigner material, Liquid CT descriptor, extended private keys (`xprv` / `zprv` imports), Lightning macaroon / CLN rune / TLS PEM / NWC URI.

**Not session-wrapped (still in EncryptedSharedPreferences when stored there):** watch-only xpubs / descriptors / single addresses, multisig public configs, PSBT signing sessions, labels, Electrum cert fingerprints, wallet metadata, and similar non-seed material.

When the app locks, the in-memory master key is zeroed (`lockSpendSecretSession`). Without a successful unlock, spend secrets cannot be decrypted even if encrypted prefs are readable on a compromised filesystem snapshot that still requires the Keystore path—defense is layered, not absolute (see Threat model).

**Disabling app lock** (`SecurityMethod.NONE`) deliberately migrates spend secrets back to EncryptedSharedPreferences values **without** the session wrap (so the wallet remains usable with no unlock), then zeros the in-memory master key. Encrypted prefs still protect at rest via the Keystore master key.

Biometric Keystore keys use `setInvalidatedByBiometricEnrollment(false)` so adding/removing fingerprints does **not** permanently orphan the spend-secret master key. Legacy invalidated keys are deleted and recreated so biometric can be re-enrolled. Biometric is **BIOMETRIC_STRONG** only (no device-credential fallback on the crypto-bound path).

### What is never logged

Mnemonics, private keys, passphrases, PINs, macaroons, runes, NWC URIs, and similar material must not appear in logs. Prefer `SecureLog` (debug-only detail; release uses static `releaseMessage` only). Release builds also strip `Log.v` / `Log.d` / `Log.i` via ProGuard `-assumenosideeffects`. Remaining `Log.w` / `Log.e` call sites should stay free of secrets.

---

## App lock

### Methods

| Method | Behavior |
|--------|----------|
| None | No app lock (not recommended for mainnet funds) |
| PIN | 4–12 digits |
| Biometric | Fingerprint / face via system BiometricPrompt bound to crypto |

### PIN hashing

- Algorithm: **PBKDF2WithHmacSHA256**
- Iterations: **150 000**
- Salt: **16 random bytes** per PIN
- Compare: **constant-time** (`MessageDigest.isEqual`)
- Password buffers cleared after derive

Legacy plaintext PINs (if any) are migrated to hashed form on successful verify and still count toward rate limiting.

### Lock timing

Configurable:

- Disabled  
- When minimized  
- On screen off  
- After 1 minute  
- After 5 minutes  

On lock: spend-secret session zeroed; L1 wallet unloaded from memory and Electrum disconnected; L2 wallets unloaded; NFC HCE payload cleared and reader/HCE preference released; optional clipboard clear (see Clipboard).

Timed locks use wall-clock background timestamps. System pickers can skip one background lock for up to 30s so in-flight SAF/camera results are not interrupted.

### Spend PIN

Optional: require the unlock PIN again before signing/broadcast (and selected Ark lifecycle actions). Uses the existing unlock PIN hash—no separate spend secret. Only available when security method is PIN. In duress mode, spend confirmation accepts the **duress** PIN only.

### Sensitive re-auth

Viewing key material and similar sensitive UI paths can require PIN/biometric again via the same unlock method (persona-scoped in duress).

### Per-wallet locks

Individual wallets can be flagged locked in metadata. Opening or unlocking a locked wallet requires app PIN/biometric (persona-scoped in duress). This is a **UI/session authorization gate**, not a second encryption layer over that wallet’s secrets. Disabling global security clears wallet locks. Requires app security to be enabled before locking a wallet.

### Session unlock flag

`isAppSessionUnlocked` is process-memory only. Configuration-change recreation can keep the session if the ViewModel survives; process death re-locks. Cloak bypass within a process is likewise session-only so rotation does not bounce back to the calculator after unlock.

---

## PIN rate limiting and lockout

After **5 failed unlock PIN attempts**, exponential backoff lockout applies:

- Base: **30 seconds**, doubles per additional failure, capped at **6 doublings**
- Failed-attempt counter is **shared** between real unlock PIN and failed guesses (not correct duress/wipe)
- Lockout state is written with **synchronous `commit`** so a process kill cannot regress auto-wipe progress

Lockout is enforced with **both**:

1. Wall-clock deadline (`System.currentTimeMillis`)
2. Monotonic duration (`SystemClock.elapsedRealtime`)

So changing the device clock or a simple reboot cannot trivially clear the same-boot lockout. After reboot, the monotonic arm is dropped (elapsed resets); only the wall-clock deadline remains—treating a negative elapsed delta as “still locked” would brick the real PIN for the entire uptime.

**Special cases (verified before lockout where applicable):**

| Input | During lockout |
|-------|----------------|
| Correct **duress** PIN | Always succeeds; resets failed counter / lockout; never triggers auto-wipe |
| Correct **wipe** PIN | Always succeeds and wipes; does not unlock |
| Correct unlock PIN | Subject to lockout when locked out |
| Biometric failures | OS rate-limits; do not increment the app failed-PIN counter toward auto-wipe |

---

## Duress mode

**Purpose:** Plausible deniability under coercion.

1. User sets a **duress PIN** (must differ from unlock PIN and wipe PIN) and a **decoy wallet** seed (optional passphrase / derivation / address type).
2. Decoy wallet is created with a generic name (`Wallet`), real wallet ID is recorded, then the real wallet remains active until coercion.
3. Entering the duress PIN on the lock screen **silently** loads the decoy wallet (`enterDuressMode`).
4. Real PIN or biometric exits duress and restores the real wallet (`exitDuressMode`).
5. Wallet list: duress mode shows only the decoy; normal mode **hides** the decoy wallet.
6. `isDuressMode` is **not** a durable user preference across cold start, but if the process dies while the **active wallet ID** is the decoy, unlock reconciles duress UI filtering from that persisted active ID (so decoy-only listing is restored). Real PIN/biometric still exits to the recorded real wallet.
7. In duress UI, Security hides Duress PIN, Auto-Wipe, and Cloak Mode cards.
8. With biometric + duress: PIN pad accepts **duress PIN only**; real wallet via biometric on a **hidden** control; biometric auto-cancels after ~2s in that mode.
9. Disabling duress deletes decoy L2 data directories (Ark / Spark / Liquid / Lightning node state as applicable) before metadata wipe.
10. Full-backup export/import filters the opposite persona’s wallets so dual-wallet setup is not leaked through backup UI.

Duress is not a substitute for full-disk encryption, a strong unlock, or operational security under a sophisticated adversary.

---

## Auto-wipe

Optional thresholds after failed PIN attempts: **Disabled / 1 / 3 / 5 / 10**.

When `failedPinAttempts >= threshold`, the app runs a **full wipe** and kills the process. Hidden in duress mode; faded when no security method is enabled.

Correct duress PIN never increments toward auto-wipe.

---

## Wipe PIN

Optional dedicated PIN that **erases all app data** when entered on the lock screen (or, with Cloak Mode, in the calculator via `=`).

- Same PBKDF2 + salt scheme as unlock PIN  
- Must differ from unlock, duress, and cloak unlock codes  
- Verified even during lockout  
- Does **not** unlock spend secrets  

---

## Full wipe procedure

Triggered by auto-wipe, Wipe PIN, or cloak calculator emergency wipe. Steps are isolated so one failure does not skip the rest; residue is verified afterward. The repository wipe is **retried once** if the first pass reports residue. On completion the process is **always killed** (`Process.killProcess`), even on partial wipe.

Typical destructive steps include:

- Disconnect network / unload wallets / prepare L2 wipe hooks  
- Delete BDK wallet databases and `filesDir` BDK tree  
- Delete LWK (`lwk/`), Spark (`spark/`), Ark (`ark/`) trees and legacy in-app Ark auto-backup dir  
- Clear Electrum cache SQLite  
- Clear clipboard  
- Stop Tor and wipe Tor data (`app_torservice`)  
- Clear encrypted + regular SharedPreferences with `commit = true`  
- Delete SharedPreferences files from disk  
- Delete Android Keystore entries: EncryptedSharedPreferences master key and biometric key (`ibis_biometric_key`)  
- Reset in-memory wallet state  
- Verify residue (prefs files, Keystore aliases, BDK/LWK/Spark/Ark dirs, wallet ids)

**Not wiped by design:** user-chosen **external** Ark DB backups (SAF folder). Those are user-owned and may outlive wallet delete / auto-wipe—required for Ark recovery because seed alone does not restore VTXO state.

---

## Cloak Mode

Disguises the app as a **Calculator**:

- Launcher activity-alias swap: Ibis icon ↔ Calculator icon (deferred to next cold start)  
- Calculator UI accepts a secret unlock code then `=`  
- Optional Wipe PIN via calculator `=`  
- Recent-apps label shows “Calculator”  
- Cloak prefs use **synchronous `commit`** before `exitProcess` so state survives restart  
- Restart path: stop Tor, start `MainActivity` via explicit intent, exit process  
- Cloak unlock code is stored in encrypted prefs as a string (constant-time compare); it is **not** PBKDF2-hashed like unlock/duress/wipe PINs  

Cloak Mode is **UI/OS surface** deniability, not cryptographic hiding of the APK from a forensic examiner with full disk access.

---

## UI and OS hardening

| Control | Default / notes |
|---------|-----------------|
| `FLAG_SECURE` | On by default (blocks screenshots / screen record / non-secure displays); togglable in Security |
| Tapjacking | `window.decorView.filterTouchesWhenObscured = true` |
| `android:allowBackup` | `false` |
| `fullBackupContent` / `data_extraction_rules` | Exclude **all** domains (root, sharedpref, database, file) from auto-backup, cloud backup, and device transfer |
| Privacy mode | Eye toggle; amounts shown as `****` across L1/L2 surfaces; persisted |
| Clipboard | Optional clear on lock / background / app close (see Clipboard) |
| Launch surface | `LaunchActivity` not exported; only launcher aliases reach it and forward to `MainActivity` |
| Deep links | Exported `MainActivity` handles `bitcoin:`, `lightning:`, `liquidnetwork:`, `liquid:` VIEW intents only; payload becomes pending send input after unlock |
| NFC | No global `NDEF_DISCOVERED` filter; reader mode only on explicit send/balance screens; HCE only while receive payloads are set |
| Notifications | Wallet activity notifications **opt-in** (default off) + runtime `POST_NOTIFICATIONS` where required |
| Keep-alive FGS | Optional connectivity foreground service (`dataSync`); **default off** |
| Permissions | `INTERNET`, network state, optional camera/NFC, notifications, FGS types as declared; camera/NFC hardware `required="false"` |

### Network security config

Cleartext is permitted at the Android network-security level because users may run **LAN Electrum / self-hosted** endpoints. The app does not load arbitrary web content. Prefer TLS + TOFU or Tor onion for untrusted networks.

### Exported components

| Component | Exported | Notes |
|-----------|----------|-------|
| `LaunchActivity` | No | Splash/forwarder only |
| `LauncherDefault` / `LauncherCalculator` | Yes | Launcher aliases → LaunchActivity |
| `MainActivity` | Yes | singleTask; payment URI VIEW filters only |
| `NdefHostApduService` | Yes | Requires `BIND_NFC_SERVICE` |
| `ConnectivityForegroundService` | No | dataSync FGS |
| `TorService` | No | specialUse FGS (`tor_anonymity_network`) |

---

## Clipboard

- Sensitive copies go through `SecureClipboard`: generic clip label, `EXTRA_IS_SENSITIVE` on Android 13+, auto-clear after **30 seconds**
- Optional modes: Disabled / On lock / On app close / On lock and close / When minimized
- Full wipe always clears the primary clip

---

## Network and privacy

### Offline by default

The app does **not** auto-connect to Electrum until the user has selected a server (and has not explicitly disconnected). Fee/price/update HTTP is off until the user enables those sources. Optional notifications and connectivity keep-alive are off by default.

### Tor

Built-in Tor (no Orbot required). SOCKS typically on `127.0.0.1:9050` (control-reported port if non-default). Electrum, fee/price clients, LN node clients, and selected L2 relays can route via SOCKS with DNS forced through the proxy (`SocksProxyHostnameDns` / equivalent) to reduce leaks.

Notes:

- `TorManager.start` / `stop` are synchronized to avoid native races.  
- Full wipe stops Tor and deletes Tor data.  
- Some L2 stacks have limitations (e.g. certain ASP HTTPS paths may not be Tor-routed depending on bindings)—check in-app behavior for the rail you use.  
- Clearnet OkHttp paths that are not Tor-routed may use `PreferIpv4Dns` for reliability (not a privacy feature).

### Electrum SSL — Trust On First Use (TOFU)

Clearnet Electrum SSL uses `TofuTrustManager`:

| Case | Behavior |
|------|----------|
| `.onion` | Trust cert (Tor authenticates the onion service); proxy may use a private trust-all factory only when no TOFU manager is attached |
| No stored fingerprint | `CertificateFirstUseException` → user must approve |
| Fingerprint match | Accept |
| Fingerprint change | `CertificateMismatchException` → warn (possible MITM) |

Approved fingerprints are stored for later sessions. Verbose tx cache entries are rejected if the server’s reported txid does not match the requested txid.

### URL validation

- Fee/price/custom explorer base URLs: `ServerUrlValidator` (http/https, no credentials/query/fragment, path traversal rejected; host validated without relying on open redirects)
- Electrum / Liquid Electrum hosts: `validateHostAndPort`
- Ark ASP / Esplora: `ArkEndpointValidator` — clearnet **https only**; `.onion` may use http (Tor)
- Full-backup restore re-validates custom URLs and Ark endpoints; invalid values are skipped
- Ark auto-backup SAF folder URI is **not** restored across installs (device-local)

### Fee and price APIs

Optional sources (mempool.space clearnet/onion, bitview.space, Electrum, custom URL, CoinGecko, Yadio, etc.). Tor-aware HTTP clients use SOCKS and avoid local DNS when Tor is selected. Response bodies are size-capped (`InputLimits`).

### App update check

Optional GitHub Releases API poll (`AppUpdateService`). Default **off** (welcome/opt-in). Release page URLs opened in a browser are restricted to `https://github.com` / `www.github.com`; anything else falls back to the official releases page. Notes length is capped. This is **not** APK signature verification—users must still verify downloads through their usual channel.

### Lightning Node TLS

When connecting to a remote LND/CLN node:

- Optional PEM pin (and optional mTLS client identity when the paste includes client key+cert)
- Clearnet TLS without a pasted cert may use trust-all for self-signed home nodes—prefer pasting `tls.cert`
- User-chosen plain HTTP is allowed for LAN
- Hostname verification is disabled once the user-selected trust policy is applied (pin or trust-all)—pinning is the integrity control when a cert is supplied
- Tor: SOCKS + DNS-through-proxy; HTTP/1.1 preferred over Tor

### Remote credentials

LND macaroon, CLN rune, TLS PEM, and NWC URI are spend secrets. Host/port/type may live in regular prefs. Compromise of those credentials is compromise of the **remote node’s** funds authority—not only the Ibis app sandbox.

### NWC (NIP-47)

NWC URI parsed strictly (`nostr+walletconnect://`, hex pubkey/secret, `ws://`/`wss://` relays). Payloads use **NIP-44 v2** (secp256k1 ECDH + HKDF + ChaCha20 + HMAC-SHA256) with legacy **NIP-04** decrypt fallback for older wallets.

### Deep links and external send input

Incoming VIEW intents and NFC reads only accept recognized payment formats (`isRecognizedSendInput` / payment URI schemes). Input is held as pending send state and consumed after unlock—not auto-broadcast.

### Explorer / browser opens

Onion explorer links prefer Tor Browser package when available; otherwise standard VIEW intents. Update/release links are sanitized as above.

---

## Backups

### Full app backup (export/import)

User-initiated JSON backups can include wallets, settings, labels, optional servers, and optional embedded Ark DB blobs.

- Optional password: **AES-256-GCM** with **PBKDF2-HMAC-SHA256** at **600 000** iterations, random 16-byte salt, 12-byte IV (`CryptoUtils`)
- Temporary password char arrays used for PBKDF2 are cleared after use (the caller still holds a Java `String` for the operation duration)
- Unencrypted export is allowed if the user opts out of a password—treat as seed-equivalent
- File reads are capped (`InputLimits.BACKUP_FILE_BYTES`)
- Labels from external files are sanitized (`BitcoinUtils.sanitizeExternalLabel` / BIP329 path)
- **Security-screen settings are intentionally excluded** from full-backup app settings (PIN/duress/wipe/cloak/auto-wipe/screenshot/clipboard modes are not round-tripped as unlock material)
- Duress persona filtering applies to which wallets appear in backup UI
- Restore validates server hosts/ports and custom URLs; clamps gap limits; skips malformed wallet entries without aborting the whole restore

Backups that contain seeds are as sensitive as the wallet itself. Store encrypted backups offline; never commit them to git or cloud sync without encryption you control.

### Ark DB backups

Ark (Bark) state is **not** recoverable from seed alone. Encrypted DB backups use:

- Magic `IBARKENC`, version byte, 12-byte nonce, AES-GCM ciphertext  
- Key: **HKDF-SHA256** from BIP39 seed (`ibis-ark-backup` / `ibis-ark-backup-v1`)  
- Wrong seed → authentication failure (treated as wrong wallet)  
- Legacy plaintext zip still accepted by unwrap helpers when not encrypted  
- External SAF backups survive app wipe by design  
- Atomic install of zip payload; auto-backup to user SAF folder when configured  

### BIP329 labels

Import sanitizes labels; BIP329 `spendable` flags only apply to valid Bitcoin outpoint refs (cannot freeze arbitrary garbage strings).

---

## Layer 2 notes

| Rail | Secret / data handling |
|------|-------------------------|
| Liquid (LWK) | Seed-derived; Signer in memory; DB under `filesDir/lwk/`; CT descriptor as spend secret when stored |
| Spark | Seed wallets; SDK data under `filesDir/spark/<walletId>` |
| Ark | Bark data under `filesDir/ark/<walletId>`; encrypted external DB backups; native handle open/close serialized |
| Lightning Node | Remote auth secrets in SecureStorage; not a local hot LN keystore for NWC/LND/CLN credentials beyond what you paste |

Swaps (Boltz, SideSwap, etc.) involve third-party coordinators—standard swap counterparty and privacy tradeoffs apply. Loopback Tor relays may be used for onion Boltz/Esplora where bindings lack SOCKS.

---

## Cryptography reference

| Use | Construction |
|-----|----------------|
| EncryptedSharedPreferences | AES-256-GCM values, AES-256-SIV keys, Keystore master |
| Spend secret blob | AES-256-GCM, 12-byte IV, 128-bit tag |
| PIN hash / PIN wrap | PBKDF2-HMAC-SHA256, 150 000 iter, 16-byte salt |
| Backup file password | PBKDF2-HMAC-SHA256, 600 000 iter → AES-256-GCM |
| Ark backup | HKDF-SHA256 → AES-256-GCM |
| Biometric | Keystore AES/CBC/PKCS7, user auth required per use |
| BIP39 | Standard PBKDF2 with `"mnemonic"` salt (via wallet stack) |
| Electrum seeds | PBKDF2 with `"electrum"` salt where applicable |
| NWC | NIP-44 v2; legacy NIP-04 AES-CBC fallback |

Randomness: `SecureRandom` for salts, IVs, and spend master keys.

---

## NFC

- **Receive:** HCE NDEF service broadcasts payment URIs only while a receive payload is set; cleared on dispose / lock. Prefer-service while receive screens request it.  
- **Send:** Reader mode only on specific screens; no manifest-wide NFC intercept. Flags: `NFC_A|B|F|V` + `NO_PLATFORM_SOUNDS` (does **not** skip NDEF check).  
- Read path: platform cached NDEF, then ISO-DEP Type 4 fallback for HCE peers.  
- Payload schemes: `bitcoin:`, `lightning:`, `liquidnetwork:`, `liquid:` (text fallback).  
- NFC hardware is optional (`required="false"`); feature off by default in settings.

Treat NFC like showing a QR: anyone who can tap while receive is active can read the invoice/address payload.

---

## Input bounds

External files, QR payloads, and HTTP JSON bodies are size-limited (`InputLimits`: small/medium/large JSON, backup, tx file, QR payload/parts). Prefer these helpers for new network/file parsers.

---

## Threat model

### Defends against (best effort)

- Casual device access when app lock is enabled  
- Shoulder surfing of balances (privacy mode, `FLAG_SECURE`)  
- Coercion scenarios where a decoy wallet or wipe is acceptable  
- Automated cloud/device backup exfiltration of app data  
- Basic screenshot / overlay touch attacks  
- Clearnet Electrum cert swap after first approved TOFU pin  
- Network observers when Tor is correctly used end-to-end for that path  
- Brute-force PIN guessing without rate limit / auto-wipe  
- Trivial clipboard lingering of sensitive copies (auto-clear + optional lock/background clear)  
- Oversized backup/HTTP/QR inputs via hard caps  

### Does not fully defend against

- Compromised or rooted Android OS, malicious accessibility services, or device admin malware  
- Physical access with chip-off / unlocked bootloader / debug bridges when the device is unlocked or Keystore is extractable  
- User who disables lock, shares seed, or stores unencrypted backups  
- Malicious or compromised Electrum / explorer / ASP / swap / LN node the user chose  
- Supply-chain compromise of the APK distribution channel if the user sideloads an unofficial build  
- Advanced forensic recovery of wiped flash (auto-wipe is best-effort secure delete of app-owned files, not full-disk crypto erase)  
- Traffic analysis when not using Tor, or when a subsystem cannot Tor-route  
- Social engineering and $5 wrench attacks beyond duress/wipe features  
- Cloak Mode against a forensic examiner who inspects installed packages / APKs  
- Optional trust-all TLS to self-hosted LN nodes without a pinned cert  

**Hardware security modules and air-gapped signing** (PSBT / animated QR) remain stronger for large cold storage than a hot phone wallet.

---

## Building and verifying releases

- Prefer official [GitHub Releases](https://github.com/aeonBTC/IbisWallet/releases).  
- Build from source: JDK 17, `./gradlew :app:assembleRelease` (signing config required for release).  
- Min SDK 26; target/compile SDK per project (`app/build.gradle.kts`); ABI: `armeabi-v7a`, `arm64-v8a`.  
- Release: `isMinifyEnabled` + `isShrinkResources` with `proguard-rules.pro` (keeps UniFFI/JNA bindings, TOFU types, crypto, Tor, biometric).  
- Review dependency pins in `gradle/libs.versions.toml`.  
- Do **not** put secrets in `gradle.properties` committed to VCS (e.g. inject `SPARK_API_KEY` via env / local property only into `BuildConfig`).  
- In-app update check does not replace verifying APK authenticity out-of-band.

AI-assisted audits are mentioned in the README; they are **not** a substitute for independent professional review. Most recent public audit note: see README / release tags.

---

## Development security guidelines

For contributors and automation:

- Never log mnemonics, private keys, passwords, PINs, or LN auth material.  
- Use `SecureStorage` spend-secret APIs for new **seed-equivalent / remote-auth** secrets; do not store new spend capability only in regular prefs.  
- Prefer `SecureLog` over raw `Log` in wallet paths; gate remaining diagnostics on `BuildConfig.DEBUG` where detail is needed.  
- Sanitize external strings (labels, URLs) before display or persistence.  
- Validate URLs before opening in a browser; keep GitHub release URL allowlisting if extending update UI.  
- Keep wipe / lockout / cloak writes on critical paths using `commit = true` when process death follows.  
- Do not re-enable Android auto-backup or weaken `data_extraction_rules`.  
- New persisted settings that affect security must round-trip in encrypted backup export/import or be explicitly documented as excluded (security-screen secrets stay excluded).  
- Bound untrusted IO with `InputLimits` / `readBytesWithLimit` / `stringWithLimit`.  
- When changing PIN length, wipe scope, Keystore aliases, backup crypto, Tor defaults, TOFU, or duress, update this file in the same change set.

### Unit-test anchors (non-exhaustive)

Security-adjacent pure logic is covered under `app/src/test`, including: `CryptoUtilsTest`, `ArkBackupCryptoTest`, `TofuTrustManagerTest`, `ServerUrlValidatorTest`, `ArkEndpointValidatorTest`, `BackupJsonAdaptersTest`, `NwcUriParserTest`, NFC reader-request registry tests.

---

## License and warranty

Ibis Wallet is open source under the [MIT License](LICENSE). THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND. You are solely responsible for your keys, backups, server choices, and operational security.

---

## Document maintenance

When changing security-relevant behavior (PIN parameters, wipe scope, Keystore aliases, backup crypto, Tor defaults, TOFU, duress, exported components, secret classification), update this file in the same change set.
