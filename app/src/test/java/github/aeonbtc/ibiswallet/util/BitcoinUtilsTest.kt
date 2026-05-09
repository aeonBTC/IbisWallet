package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.AddressType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

class BitcoinUtilsTest : FunSpec({

    // ══════════════════════════════════════════════════════════════════
    // detectAddressType — determines how the app interprets addresses.
    // A bug here could send funds to the wrong address type.
    // ══════════════════════════════════════════════════════════════════

    context("detectAddressType") {

        // ── Mainnet ──

        test("P2PKH (Legacy) - starts with 1") {
            BitcoinUtils.detectAddressType("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2") shouldBe AddressType.LEGACY
        }

        test("returns null for unsupported nested SegWit address starting with 3") {
            BitcoinUtils.detectAddressType("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy") shouldBe null
        }

        test("P2WPKH (SegWit) - starts with bc1q") {
            BitcoinUtils.detectAddressType("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4") shouldBe AddressType.SEGWIT
        }

        test("P2TR (Taproot) - starts with bc1p") {
            BitcoinUtils.detectAddressType("bc1p5cyxnuxmeuwuvkwfem96lqzszee02v05nflt6jd3hl97e8m50m3sardyj6p") shouldBe AddressType.TAPROOT
        }

        test("returns null for unsupported nested SegWit testnet address starting with 2") {
            BitcoinUtils.detectAddressType("2MzQwSSnBHWHqSAqtTVQ6v47XtaisrJa1Vc") shouldBe null
        }

        // ── Edge cases ──

        test("returns null for empty string") {
            BitcoinUtils.detectAddressType("") shouldBe null
        }

        test("returns null for random string") {
            BitcoinUtils.detectAddressType("hello world") shouldBe null
        }

        test("trims whitespace") {
            BitcoinUtils.detectAddressType("  bc1qtest  ") shouldBe AddressType.SEGWIT
        }

        test("returns null for unknown prefix") {
            BitcoinUtils.detectAddressType("X1234567890") shouldBe null
        }
    }

    context("detectDescriptorAddressType") {

        test("detects pkh descriptor as Legacy") {
            BitcoinUtils.detectDescriptorAddressType("pkh([73c5da0a/44'/0'/0']xpub6CUG.../0/*)") shouldBe AddressType.LEGACY
        }

        test("detects wpkh descriptor as SegWit") {
            BitcoinUtils.detectDescriptorAddressType("wpkh([73c5da0a/84'/0'/0']xpub6CUG.../0/*)") shouldBe AddressType.SEGWIT
        }

        test("detects tr descriptor as Taproot") {
            BitcoinUtils.detectDescriptorAddressType("tr([73c5da0a/86'/0'/0']xpub6CUG.../0/*)") shouldBe AddressType.TAPROOT
        }

        test("ignores checksum suffix") {
            BitcoinUtils.detectDescriptorAddressType("wpkh([73c5da0a/84'/0'/0']xpub6CUG.../0/*)#deadbeef") shouldBe AddressType.SEGWIT
        }

        test("returns null for unsupported descriptors") {
            BitcoinUtils.detectDescriptorAddressType("sh(wpkh([73c5da0a/49'/0'/0']xpub6CUG.../0/*))") shouldBe null
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // isWatchOnlyInput — determines if key material is watch-only.
    // A mistake here could reject valid watch-only keys or treat a
    // private key as watch-only (preventing spending).
    // ══════════════════════════════════════════════════════════════════

    context("isWatchOnlyInput") {

        // ── Bare extended public keys ──

        test("bare xpub is watch-only") {
            BitcoinUtils.isWatchOnlyInput("xpub6CUGRUonZSQ4TWtTMmzXdrXDtypWKiKrhko4egpiMZbpiaQL2jkwSB1icqYh2cfDfVxdx4df189oLKnC5fSwqPfgyP3hooxujYzAu3fDVmz") shouldBe true
        }

        test("bare zpub is watch-only") {
            BitcoinUtils.isWatchOnlyInput("zpub6rFR7y4Q2AijBEqTUqoBDZJ2vCdV2LfVKKbvaC9HUi7rFLzakpmivC9RAfMbGRsAUPG2uB1y5XP1KCPBsqCDuKa") shouldBe true
        }

        test("bare ypub is rejected") {
            BitcoinUtils.isWatchOnlyInput("ypub6Ww3ibxVfGzLrAH1PNcjyAWenMTbbAoFN4w2EFaUS8fEaaDEDfCUKB3Tusi32mNx37Z7VBmwSCmbrGMKFAGKS19hVuL5B33v6TxTJ6BPNXkp") shouldBe false
        }

        test("bare tpub is rejected") {
            BitcoinUtils.isWatchOnlyInput("tpub6BkU4YTk6v2qMa39Y7SDNP5Y") shouldBe false
        }

        test("bare vpub is rejected") {
            BitcoinUtils.isWatchOnlyInput("vpub5Y6cjg78GGuNLsaPhmYsiw4gYX3HoQiRBiSwDaBXKUafCt9bNwWQiitDk5VZ5BVxYnQdwoTyXSs2JHRPAgjAvtbBrf8ZhDYe2jWAckNfkS") shouldBe false
        }

        test("bare upub is rejected") {
            BitcoinUtils.isWatchOnlyInput("upub5EFU65HtV5TeiSHmZZm7FUffBGy8UKeqp7vw43jYbvZPpoVsgU93oac") shouldBe false
        }

        // ── Origin-prefixed ──

        test("origin-prefixed xpub is watch-only") {
            BitcoinUtils.isWatchOnlyInput("[73c5da0a/84'/0'/0']xpub6CUGRUonZSQ4T...") shouldBe true
        }

        test("origin-prefixed zpub is watch-only") {
            BitcoinUtils.isWatchOnlyInput("[d34db33f/84'/0'/0']zpub6rFR7...") shouldBe true
        }

        // ── Output descriptors ──

        test("wpkh descriptor with xpub is watch-only") {
            BitcoinUtils.isWatchOnlyInput("wpkh([73c5da0a/84'/0'/0']xpub6CUG.../0/*)") shouldBe true
        }

        test("pkh descriptor with xpub is watch-only") {
            BitcoinUtils.isWatchOnlyInput("pkh([73c5da0a/44'/0'/0']xpub6CUG.../0/*)") shouldBe true
        }

        test("tr descriptor with xpub is watch-only") {
            BitcoinUtils.isWatchOnlyInput("tr([73c5da0a/86'/0'/0']xpub6CUG.../0/*)") shouldBe true
        }

        test("wsh multisig descriptor with xpubs is watch-only") {
            BitcoinUtils.isWatchOnlyInput(
                "wsh(sortedmulti(2,[73c5da0a/48'/0'/0'/2']xpub6CUG.../0/*,[d34db33f/48'/0'/0'/2']xpub6DEF.../0/*))",
            ) shouldBe true
        }

        test("sh(wpkh) descriptor with xpub is rejected") {
            BitcoinUtils.isWatchOnlyInput("sh(wpkh([73c5da0a/49'/0'/0']xpub6CUG.../0/*))") shouldBe false
        }

        // ── Negative cases ──

        test("mnemonic is NOT watch-only") {
            BitcoinUtils.isWatchOnlyInput("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about") shouldBe false
        }

        test("random string is NOT watch-only") {
            BitcoinUtils.isWatchOnlyInput("hello world") shouldBe false
        }

        test("empty string is NOT watch-only") {
            BitcoinUtils.isWatchOnlyInput("") shouldBe false
        }

        test("descriptor with xprv is NOT watch-only") {
            BitcoinUtils.isWatchOnlyInput("wpkh([73c5da0a/84'/0'/0']xprv9s21ZrQH143K.../0/*)") shouldBe false
        }

        test("descriptor with tprv is NOT watch-only") {
            BitcoinUtils.isWatchOnlyInput("wpkh([73c5da0a/84'/0'/0']tprv8ZgxMBicQKs.../0/*)") shouldBe false
        }

        test("WIF private key is NOT watch-only") {
            BitcoinUtils.isWatchOnlyInput("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73Nd2Mcv1") shouldBe false
        }

        // ── Whitespace handling ──

        test("trims whitespace") {
            BitcoinUtils.isWatchOnlyInput("  xpub6CUGRUonZSQ4T  ") shouldBe true
        }
    }

    context("unsupportedNestedSegwitReason") {
        test("returns message for bare ypub") {
            BitcoinUtils.unsupportedNestedSegwitReason("ypub6Test") shouldBe
                BitcoinUtils.UNSUPPORTED_NESTED_SEGWIT_MESSAGE
        }

        test("returns message for sh(wpkh) descriptor") {
            BitcoinUtils.unsupportedNestedSegwitReason("sh(wpkh([fp]xpub/0/*))") shouldBe
                BitcoinUtils.UNSUPPORTED_NESTED_SEGWIT_MESSAGE
        }

        test("returns message for nested SegWit address") {
            BitcoinUtils.unsupportedNestedSegwitReason("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy") shouldBe
                BitcoinUtils.UNSUPPORTED_NESTED_SEGWIT_MESSAGE
        }
    }

    context("unsupportedNonMainnetReason") {
        test("returns message for tb1 address") {
            BitcoinUtils.unsupportedNonMainnetReason("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx") shouldBe
                BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE
        }

        test("returns message for testnet xpub") {
            BitcoinUtils.unsupportedNonMainnetReason("tpub6BkU4YTk6v2qMa39Y7SDNP5Y") shouldBe
                BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE
        }

        test("returns message for testnet WIF") {
            BitcoinUtils.unsupportedNonMainnetReason("cNYfRxoekiKMJRQDLLMnBswRorJkNv1GvmFBRABATcwRhJhFLReB") shouldBe
                BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE
        }

        test("does not reject mnemonic starting with m") {
            BitcoinUtils.unsupportedNonMainnetReason("move angry ethics matrix proud vanish cricket density unusual expose window grocery") shouldBe
                null
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // isWifPrivateKey — validates WIF-encoded private keys.
    // A false positive could allow importing invalid keys (no funds).
    // A false negative could reject valid keys (can't sweep).
    // ══════════════════════════════════════════════════════════════════

    context("isWifPrivateKey") {

        // ── Valid mainnet WIF keys ──

        test("valid compressed mainnet WIF starting with K") {
            // This is the WIF for private key = 1 (well-known test vector)
            BitcoinUtils.isWifPrivateKey("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73Nd2Mcv1") shouldBe true
        }

        test("valid compressed mainnet WIF starting with L") {
            // Private key = 2 (compressed, mainnet) — well-known test vector
            BitcoinUtils.isWifPrivateKey("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU74sHUHy8S") shouldBe true
        }

        test("valid uncompressed mainnet WIF starting with 5") {
            BitcoinUtils.isWifPrivateKey("5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ") shouldBe true
        }

        // ── Invalid WIF keys ──

        test("wrong length rejects fast") {
            BitcoinUtils.isWifPrivateKey("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjg") shouldBe false
        }

        test("wrong prefix rejects fast") {
            BitcoinUtils.isWifPrivateKey("AwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73Nd2Mcv1") shouldBe false
        }

        test("bad checksum fails validation") {
            // Modify last char to corrupt checksum
            BitcoinUtils.isWifPrivateKey("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73Nd2Mcv2") shouldBe false
        }

        test("mnemonic is not WIF") {
            BitcoinUtils.isWifPrivateKey("abandon abandon abandon") shouldBe false
        }

        test("xpub is not WIF") {
            BitcoinUtils.isWifPrivateKey("xpub6CUGRUonZSQ4TWtTMmzXdrXDtypWKiKrhko4egpiMZbpiaQL2jkw") shouldBe false
        }

        test("empty string is not WIF") {
            BitcoinUtils.isWifPrivateKey("") shouldBe false
        }

        test("trims whitespace") {
            BitcoinUtils.isWifPrivateKey("  KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73Nd2Mcv1  ") shouldBe true
        }
    }

    // ── isWifCompressed ──

    context("isWifCompressed") {
        test("K prefix 52 chars is compressed") {
            BitcoinUtils.isWifCompressed("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73Nd2Mcv1") shouldBe true
        }

        test("L prefix 52 chars is compressed") {
            BitcoinUtils.isWifCompressed("L5oLkpV3aqBjhki6LmvChTCV6odsp4SXM6VfSoEuocCeFEFchbz1") shouldBe true
        }

        test("c prefix 52 chars is not compressed because testnet WIF is unsupported") {
            BitcoinUtils.isWifCompressed("cNYfRxoekiKMJRQDLLMnBswRorJkNv1GvmFBRABATcwRhJhFLReB") shouldBe false
        }

        test("5 prefix 51 chars is NOT compressed") {
            BitcoinUtils.isWifCompressed("5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ") shouldBe false
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // extractFingerprint — critical for hardware wallet PSBT signing.
    // Wrong fingerprint = hardware wallet can't sign transactions.
    // ══════════════════════════════════════════════════════════════════

    context("extractFingerprint") {
        test("extracts from [fingerprint/path]xpub format") {
            BitcoinUtils.extractFingerprint("[d34db33f/84'/0'/0']xpub6CUG...") shouldBe "d34db33f"
        }

        test("extracts from wpkh([fingerprint/path]xpub/0/*) descriptor") {
            BitcoinUtils.extractFingerprint("wpkh([73c5da0a/84'/0'/0']xpub6CUG.../0/*)") shouldBe "73c5da0a"
        }

        test("lowercases fingerprint") {
            BitcoinUtils.extractFingerprint("[D34DB33F/84'/0'/0']xpub6CUG...") shouldBe "d34db33f"
        }

        test("returns null for bare xpub (no origin)") {
            BitcoinUtils.extractFingerprint("xpub6CUGRUonZSQ4T...") shouldBe null
        }

        test("returns null for empty string") {
            BitcoinUtils.extractFingerprint("") shouldBe null
        }

        test("returns null for mnemonic") {
            BitcoinUtils.extractFingerprint("abandon abandon abandon") shouldBe null
        }

        test("returns null for fingerprint without path separator") {
            // Must have / after fingerprint to match pattern
            BitcoinUtils.extractFingerprint("[d34db33f]xpub6CUG...") shouldBe null
        }

        test("returns null for short fingerprint") {
            BitcoinUtils.extractFingerprint("[d34d/84'/0'/0']xpub6CUG...") shouldBe null
        }

        test("trims whitespace") {
            BitcoinUtils.extractFingerprint("  [d34db33f/84'/0'/0']xpub6CUG...  ") shouldBe "d34db33f"
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // parseKeyOrigin — parses key origin for descriptor construction.
    // Wrong parsing = wrong descriptors = wrong addresses = lost funds.
    // ══════════════════════════════════════════════════════════════════

    context("parseKeyOrigin") {
        test("parses standard format [fingerprint/path]key") {
            val result = BitcoinUtils.parseKeyOrigin("[d34db33f/84'/0'/0']xpub6CUG...")
            result.fingerprint shouldBe "d34db33f"
            result.derivationPath shouldBe "84'/0'/0'"
            result.bareKey shouldBe "xpub6CUG..."
        }

        test("normalizes H notation to apostrophe") {
            val result = BitcoinUtils.parseKeyOrigin("[d34db33f/84H/0H/0H]xpub6CUG...")
            result.derivationPath shouldBe "84'/0'/0'"
        }

        test("normalizes lowercase h notation to apostrophe") {
            val result = BitcoinUtils.parseKeyOrigin("[d34db33f/84h/0h/0h]xpub6CUG...")
            result.derivationPath shouldBe "84'/0'/0'"
        }

        test("lowercases fingerprint") {
            val result = BitcoinUtils.parseKeyOrigin("[D34DB33F/84'/0'/0']xpub6CUG...")
            result.fingerprint shouldBe "d34db33f"
        }

        test("bare key returns key with null origin") {
            val result = BitcoinUtils.parseKeyOrigin("xpub6CUGRUonZSQ4T...")
            result.bareKey shouldBe "xpub6CUGRUonZSQ4T..."
            result.fingerprint shouldBe null
            result.derivationPath shouldBe null
        }

        test("trims whitespace from bare key") {
            val result = BitcoinUtils.parseKeyOrigin("  xpub6CUG...  ")
            result.bareKey shouldBe "xpub6CUG..."
        }

        test("handles deep derivation paths") {
            val result = BitcoinUtils.parseKeyOrigin("[d34db33f/84'/0'/0'/1/2]xpub6CUG...")
            result.derivationPath shouldBe "84'/0'/0'/1/2"
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Base58 — foundation of Bitcoin address/key encoding.
    // A bug here could produce invalid addresses or reject valid ones.
    // ══════════════════════════════════════════════════════════════════

    context("Base58") {

        // ── encode / decode round-trip ──

        test("encode then decode round-trips") {
            val original = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0xFF.toByte())
            val encoded = BitcoinUtils.Base58.encode(original)
            val decoded = BitcoinUtils.Base58.decode(encoded)
            decoded.toList() shouldBe original.toList()
        }

        test("leading zero bytes preserved in round-trip") {
            val original = byteArrayOf(0x00, 0x00, 0x00, 0x01)
            val encoded = BitcoinUtils.Base58.encode(original)
            encoded shouldStartWith "111" // three leading '1's for three zero bytes
            val decoded = BitcoinUtils.Base58.decode(encoded)
            decoded.toList() shouldBe original.toList()
        }

        test("empty input encodes to empty string") {
            BitcoinUtils.Base58.encode(ByteArray(0)) shouldBe ""
        }

        test("empty string decodes to empty array") {
            BitcoinUtils.Base58.decode("").toList() shouldBe emptyList()
        }

        // ── Known Bitcoin test vector ──

        test("known Base58 vector: 'Hello World' encodes correctly") {
            val encoded = BitcoinUtils.Base58.encode("Hello World".toByteArray(Charsets.US_ASCII))
            encoded shouldBe "JxF12TrwUP45BMd"
        }

        test("known Base58 vector: decodes back to 'Hello World'") {
            val decoded = BitcoinUtils.Base58.decode("JxF12TrwUP45BMd")
            String(decoded, Charsets.US_ASCII) shouldBe "Hello World"
        }

        // ── encodeChecked / decodeChecked ──

        test("encodeChecked then decodeChecked round-trips") {
            val payload = byteArrayOf(0x80.toByte(), 0x01, 0x02, 0x03)
            val encoded = BitcoinUtils.Base58.encodeChecked(payload)
            val decoded = BitcoinUtils.Base58.decodeChecked(encoded)
            decoded.toList() shouldBe payload.toList()
        }

        test("decodeChecked rejects corrupted checksum") {
            val payload = byteArrayOf(0x80.toByte(), 0x01, 0x02, 0x03)
            val encoded = BitcoinUtils.Base58.encodeChecked(payload)
            // Corrupt last character
            val corrupted = encoded.dropLast(1) + if (encoded.last() == '1') "2" else "1"
            shouldThrow<IllegalArgumentException> {
                BitcoinUtils.Base58.decodeChecked(corrupted)
            }
        }

        test("decodeChecked rejects invalid characters") {
            shouldThrow<IllegalArgumentException> {
                BitcoinUtils.Base58.decodeChecked("0OIl") // 0, O, I, l are not in Base58
            }
        }

        // ── Known WIF test vector ──

        test("known WIF decodes to version 0x80 with valid checksum") {
            // WIF for private key = 1 (compressed, mainnet)
            val decoded = BitcoinUtils.Base58.decodeChecked("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73Nd2Mcv1")
            val version = decoded[0].toInt() and 0xFF
            version shouldBe 0x80
            decoded.size shouldBe 34 // version(1) + key(32) + compression(1)
        }

        test("known uncompressed WIF decodes to version 0x80 with 33 bytes") {
            val decoded = BitcoinUtils.Base58.decodeChecked("5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ")
            val version = decoded[0].toInt() and 0xFF
            version shouldBe 0x80
            decoded.size shouldBe 33 // version(1) + key(32), no compression flag
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // convertToXpub — converts SLIP-132 extended keys to standard xpub.
    // Wrong conversion = BDK gets wrong key = wrong addresses generated.
    // ══════════════════════════════════════════════════════════════════

    context("convertToXpub") {

        test("xpub passes through unchanged") {
            val xpub = "xpub6CUGRUonZSQ4TWtTMmzXdrXDtypWKiKrhko4egpiMZbpiaQL2jkwSB1icqYh2cfDfVxdx4df189oLKnC5fSwqPfgyP3hooxujYzAu3fDVmz"
            BitcoinUtils.convertToXpub(xpub) shouldBe xpub
        }

        test("tpub is rejected") {
            val error =
                shouldThrow<IllegalArgumentException> {
                    BitcoinUtils.convertToXpub("tpub6BkU4YTk6v2qMa39Y7SDNP5Y")
                }
            error.message shouldBe BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE
        }

        test("zpub converts to xpub") {
            // Generate a known zpub from ElectrumSeedUtil and verify conversion
            val segwitSeed = "wild father tree among universe such mobile favorite target dynamic credit identify"
            val seed = ElectrumSeedUtil.mnemonicToSeed(segwitSeed)
            val zpub = ElectrumSeedUtil.deriveExtendedPublicKey(
                seed, ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
            )
            zpub shouldStartWith "zpub"

            val converted = BitcoinUtils.convertToXpub(zpub)
            converted shouldStartWith "xpub"
            converted.length shouldBe 111 // valid BIP32 extended key length
        }

        test("converted key decodes with valid checksum") {
            val segwitSeed = "wild father tree among universe such mobile favorite target dynamic credit identify"
            val seed = ElectrumSeedUtil.mnemonicToSeed(segwitSeed)
            val zpub = ElectrumSeedUtil.deriveExtendedPublicKey(
                seed, ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
            )
            val xpub = BitcoinUtils.convertToXpub(zpub)

            // Should decode without throwing (valid checksum)
            val decoded = BitcoinUtils.Base58.decodeChecked(xpub)
            decoded.size shouldBe 78 // standard BIP32 payload

            // Version bytes should be xpub (0x0488B21E)
            (decoded[0].toInt() and 0xFF) shouldBe 0x04
            (decoded[1].toInt() and 0xFF) shouldBe 0x88
            (decoded[2].toInt() and 0xFF) shouldBe 0xB2
            (decoded[3].toInt() and 0xFF) shouldBe 0x1E
        }

        test("conversion preserves key data (only version bytes change)") {
            val segwitSeed = "wild father tree among universe such mobile favorite target dynamic credit identify"
            val seed = ElectrumSeedUtil.mnemonicToSeed(segwitSeed)
            val zpub = ElectrumSeedUtil.deriveExtendedPublicKey(
                seed, ElectrumSeedUtil.ElectrumSeedType.SEGWIT,
            )
            val xpub = BitcoinUtils.convertToXpub(zpub)

            val zpubData = BitcoinUtils.Base58.decodeChecked(zpub)
            val xpubData = BitcoinUtils.Base58.decodeChecked(xpub)

            // Everything after the 4-byte version should be identical
            zpubData.sliceArray(4 until zpubData.size).toList() shouldBe
                xpubData.sliceArray(4 until xpubData.size).toList()
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // feeRateToSatPerKwu — converts sat/vB to sat/kWU for BDK FeeRate.
    // A bug here means wrong fee rates on every transaction.
    // ══════════════════════════════════════════════════════════════════

    context("feeRateToSatPerKwu") {

        test("1.0 sat/vB = 250 sat/kWU") {
            BitcoinUtils.feeRateToSatPerKwu(1.0) shouldBe 250UL
        }

        test("0.5 sat/vB = 125 sat/kWU") {
            BitcoinUtils.feeRateToSatPerKwu(0.5) shouldBe 125UL
        }

        test("0.8 sat/vB = 200 sat/kWU") {
            BitcoinUtils.feeRateToSatPerKwu(0.8) shouldBe 200UL
        }

        test("2.0 sat/vB = 500 sat/kWU") {
            BitcoinUtils.feeRateToSatPerKwu(2.0) shouldBe 500UL
        }

        test("100.0 sat/vB = 25000 sat/kWU") {
            BitcoinUtils.feeRateToSatPerKwu(100.0) shouldBe 25000UL
        }

        test("very small rate 0.004 sat/vB clamps to 1 sat/kWU minimum") {
            // 0.004 * 250 = 1.0, rounds to 1
            BitcoinUtils.feeRateToSatPerKwu(0.004) shouldBe 1UL
        }

        test("zero sat/vB clamps to 1 sat/kWU minimum") {
            BitcoinUtils.feeRateToSatPerKwu(0.0) shouldBe 1UL
        }

        test("negative sat/vB clamps to 1 sat/kWU minimum") {
            BitcoinUtils.feeRateToSatPerKwu(-5.0) shouldBe 1UL
        }

        test("fractional rounding: 0.3 sat/vB = round(75.0) = 75 sat/kWU") {
            // 0.3 * 250 = 75.0
            BitcoinUtils.feeRateToSatPerKwu(0.3) shouldBe 75UL
        }

        test("fractional rounding: 1.1 sat/vB = round(275.0) = 275 sat/kWU") {
            BitcoinUtils.feeRateToSatPerKwu(1.1) shouldBe 275UL
        }

        test("large fee rate: 500.0 sat/vB = 125000 sat/kWU") {
            BitcoinUtils.feeRateToSatPerKwu(500.0) shouldBe 125000UL
        }

        test("0.001 sat/vB = round(0.25) = 0 -> clamps to 1 sat/kWU") {
            BitcoinUtils.feeRateToSatPerKwu(0.001) shouldBe 1UL
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // computeExactFeeSats — fee = round(rate * vsize).
    // A bug means paying wrong fee on every transaction.
    // ══════════════════════════════════════════════════════════════════

    context("computeExactFeeSats") {

        test("1 sat/vB * 140 vsize = 140 sats") {
            BitcoinUtils.computeExactFeeSats(1.0, 140.0) shouldBe 140UL
        }

        test("2.5 sat/vB * 200 vsize = 500 sats") {
            BitcoinUtils.computeExactFeeSats(2.5, 200.0) shouldBe 500UL
        }

        test("1.0 sat/vB * 141.0 vsize = 141 sats (exact)") {
            BitcoinUtils.computeExactFeeSats(1.0, 141.0) shouldBe 141UL
        }

        test("rounding: 1.5 sat/vB * 141.0 vsize = round(211.5) = 212 sats") {
            BitcoinUtils.computeExactFeeSats(1.5, 141.0) shouldBe 212UL
        }

        test("rounding: 3.3 sat/vB * 100.0 vsize = round(330.0) = 330 sats") {
            BitcoinUtils.computeExactFeeSats(3.3, 100.0) shouldBe 330UL
        }

        test("returns null for vsize = 0") {
            BitcoinUtils.computeExactFeeSats(1.0, 0.0) shouldBe null
        }

        test("returns null for negative vsize") {
            BitcoinUtils.computeExactFeeSats(1.0, -10.0) shouldBe null
        }

        test("returns null for zero rate (fee would be 0)") {
            BitcoinUtils.computeExactFeeSats(0.0, 140.0) shouldBe null
        }

        test("large transaction: 50 sat/vB * 10000 vsize = 500000 sats") {
            BitcoinUtils.computeExactFeeSats(50.0, 10000.0) shouldBe 500000UL
        }

        test("sub-sat/vB rate: 0.5 sat/vB * 200 vsize = 100 sats") {
            BitcoinUtils.computeExactFeeSats(0.5, 200.0) shouldBe 100UL
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // computeScriptHash — Electrum script hash from raw bytes.
    // A bug means address subscription failures / missed transactions.
    // ══════════════════════════════════════════════════════════════════

    context("computeScriptHash") {

        test("P2WPKH scriptPubKey produces correct reversed-SHA256 hex") {
            // Known P2WPKH scriptPubKey for bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4
            // scriptPubKey: 0014751e76e8199196d454941c45d1b3a323f1433bd6
            val scriptBytes = hexToBytes("0014751e76e8199196d454941c45d1b3a323f1433bd6")
            val scriptHash = BitcoinUtils.computeScriptHash(scriptBytes)

            // Verify it's 64 hex chars (32 bytes as hex)
            scriptHash.length shouldBe 64

            // Independently compute: SHA-256 of scriptBytes, reversed, hex-encoded
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(scriptBytes)
            val expected = hash.reversedArray().joinToString("") { "%02x".format(it) }
            scriptHash shouldBe expected
        }

        test("empty script produces SHA-256 of empty data, reversed") {
            val scriptHash = BitcoinUtils.computeScriptHash(ByteArray(0))
            // SHA-256 of empty = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
            // Reversed = 55b852781b9995a44c939b64e441ae2724b96f99c8f4fb9a141cfc9842c4b0e3
            scriptHash shouldBe "55b852781b9995a44c939b64e441ae2724b96f99c8f4fb9a141cfc9842c4b0e3"
        }

        test("P2PKH scriptPubKey for known address") {
            // P2PKH scriptPubKey for 1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2
            // OP_DUP OP_HASH160 <20 bytes> OP_EQUALVERIFY OP_CHECKSIG
            // 76a91477bff20c60e522dfaa3350c39b030a5d004e839a88ac
            val scriptBytes = hexToBytes("76a91477bff20c60e522dfaa3350c39b030a5d004e839a88ac")
            val scriptHash = BitcoinUtils.computeScriptHash(scriptBytes)
            scriptHash.length shouldBe 64
            // Just verify it's a valid hex string and deterministic
            scriptHash shouldBe BitcoinUtils.computeScriptHash(scriptBytes)
        }

        test("script hash is deterministic") {
            val script = hexToBytes("0014751e76e8199196d454941c45d1b3a323f1433bd6")
            BitcoinUtils.computeScriptHash(script) shouldBe BitcoinUtils.computeScriptHash(script)
        }

        test("different scripts produce different hashes") {
            val script1 = hexToBytes("0014751e76e8199196d454941c45d1b3a323f1433bd6")
            val script2 = hexToBytes("0014000000000000000000000000000000000000000000")
            BitcoinUtils.computeScriptHash(script1) shouldNotBe BitcoinUtils.computeScriptHash(script2)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // buildKeyWithOrigin — assembles [fp/path]xpub key expression.
    // A bug means hardware wallets can't sign PSBTs.
    // ══════════════════════════════════════════════════════════════════

    context("buildKeyWithOrigin") {

        test("with fingerprint and path uses them directly") {
            val result = BitcoinUtils.buildKeyWithOrigin(
                "xpub6ABC", "73c5da0a", "84'/0'/0'", AddressType.SEGWIT,
            )
            result shouldBe "[73c5da0a/84'/0'/0']xpub6ABC"
        }

        test("null fingerprint uses 00000000 fallback") {
            val result = BitcoinUtils.buildKeyWithOrigin(
                "xpub6ABC", null, "84'/0'/0'", AddressType.SEGWIT,
            )
            result shouldBe "[00000000/84'/0'/0']xpub6ABC"
        }

        test("null derivation path uses address type default (SEGWIT)") {
            val result = BitcoinUtils.buildKeyWithOrigin(
                "xpub6ABC", "73c5da0a", null, AddressType.SEGWIT,
            )
            result shouldBe "[73c5da0a/84'/0'/0']xpub6ABC"
        }

        test("null derivation path uses address type default (LEGACY)") {
            val result = BitcoinUtils.buildKeyWithOrigin(
                "xpub6ABC", "73c5da0a", null, AddressType.LEGACY,
            )
            result shouldBe "[73c5da0a/44'/0'/0']xpub6ABC"
        }

        test("null derivation path uses address type default (TAPROOT)") {
            val result = BitcoinUtils.buildKeyWithOrigin(
                "xpub6ABC", "73c5da0a", null, AddressType.TAPROOT,
            )
            result shouldBe "[73c5da0a/86'/0'/0']xpub6ABC"
        }

        test("both null uses fallback fingerprint and default path") {
            val result = BitcoinUtils.buildKeyWithOrigin(
                "xpub6ABC", null, null, AddressType.SEGWIT,
            )
            result shouldBe "[00000000/84'/0'/0']xpub6ABC"
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // buildDescriptorStrings — wraps key in descriptor function.
    // A bug means descriptor won't parse or wrong address type.
    // ══════════════════════════════════════════════════════════════════

    context("buildDescriptorStrings") {

        test("LEGACY produces pkh() descriptors") {
            val (ext, int) = BitcoinUtils.buildDescriptorStrings("[fp/path]xpub", AddressType.LEGACY)
            ext shouldBe "pkh([fp/path]xpub/0/*)"
            int shouldBe "pkh([fp/path]xpub/1/*)"
        }

        test("SEGWIT produces wpkh() descriptors") {
            val (ext, int) = BitcoinUtils.buildDescriptorStrings("[fp/path]xpub", AddressType.SEGWIT)
            ext shouldBe "wpkh([fp/path]xpub/0/*)"
            int shouldBe "wpkh([fp/path]xpub/1/*)"
        }

        test("TAPROOT produces tr() descriptors") {
            val (ext, int) = BitcoinUtils.buildDescriptorStrings("[fp/path]xpub", AddressType.TAPROOT)
            ext shouldBe "tr([fp/path]xpub/0/*)"
            int shouldBe "tr([fp/path]xpub/1/*)"
        }

        test("external always uses /0/* and internal /1/*") {
            for (addrType in AddressType.entries) {
                val (ext, int) = BitcoinUtils.buildDescriptorStrings("KEY", addrType)
                ext shouldContain "/0/*"
                int shouldContain "/1/*"
                ext shouldNotContain "/1/*"
                int shouldNotContain "/0/*"
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // stripDescriptorChecksum — removes #checksum suffix.
    // ══════════════════════════════════════════════════════════════════

    context("stripDescriptorChecksum") {

        test("removes checksum suffix") {
            BitcoinUtils.stripDescriptorChecksum("wpkh([fp]xpub/0/*)#abcdef12") shouldBe "wpkh([fp]xpub/0/*)"
        }

        test("no checksum passes through") {
            BitcoinUtils.stripDescriptorChecksum("wpkh([fp]xpub/0/*)") shouldBe "wpkh([fp]xpub/0/*)"
        }

        test("trims whitespace") {
            BitcoinUtils.stripDescriptorChecksum("  wpkh([fp]xpub/0/*)#abc  ") shouldBe "wpkh([fp]xpub/0/*)"
        }

        test("empty string") {
            BitcoinUtils.stripDescriptorChecksum("") shouldBe ""
        }
    }

    context("normalizeLiquidDescriptorInput") {

        val receive =
            "ct(slip77(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)," +
                "elwpkh([3cd9f751/84'/1776'/0']xpubExample/0/*))#u0sk4d67"
        val change =
            "ct(slip77(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)," +
                "elwpkh([3cd9f751/84'/1776'/0']xpubExample/1/*))#fw7qdj0p"
        val combined =
            "ct(slip77(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)," +
                "elwpkh([3cd9f751/84'/1776'/0']xpubExample/<0;1>/*))"

        test("passes through a single combined Liquid descriptor and strips checksum") {
            BitcoinUtils.normalizeLiquidDescriptorInput("$combined#deadbeef") shouldBe combined
        }

        test("combines a matching Green-style Liquid descriptor pair") {
            BitcoinUtils.normalizeLiquidDescriptorInput("$receive\n$change") shouldBe combined
        }

        test("combines a matching Green-style Liquid descriptor pair in reversed order") {
            BitcoinUtils.normalizeLiquidDescriptorInput("$change\n$receive") shouldBe combined
        }

        test("returns null for a mismatched Liquid descriptor pair") {
            val mismatched =
                "ct(slip77(bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb)," +
                    "elwpkh([3cd9f751/84'/1776'/0']xpubExample/1/*))#fw7qdj0p"
            BitcoinUtils.normalizeLiquidDescriptorInput("$receive\n$mismatched") shouldBe null
        }
    }

    context("formatLiquidDescriptorForDisplay") {

        val combined =
            "ct(slip77(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)," +
                "elwpkh([3cd9f751/84'/1776'/0']xpubExample/<0;1>/*))"
        val dual =
            listOf(
                "ct(slip77(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)," +
                    "elwpkh([3cd9f751/84'/1776'/0']xpubExample/0/*))",
                "ct(slip77(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)," +
                    "elwpkh([3cd9f751/84'/1776'/0']xpubExample/1/*))",
            ).joinToString("\n")

        test("expands combined Liquid descriptor into dual descriptors") {
            BitcoinUtils.formatLiquidDescriptorForDisplay(combined) shouldBe dual
        }

        test("strips checksum before expanding combined Liquid descriptor") {
            BitcoinUtils.formatLiquidDescriptorForDisplay("$combined#deadbeef") shouldBe dual
        }

        test("passes through single branch Liquid descriptor") {
            val single =
                "ct(slip77(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)," +
                    "elwpkh([3cd9f751/84'/1776'/0']xpubExample/0/*))"
            BitcoinUtils.formatLiquidDescriptorForDisplay(single) shouldBe single
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // isFullDescriptor — detects full output descriptor strings.
    // ══════════════════════════════════════════════════════════════════

    context("isFullDescriptor") {

        test("wpkh() is a full descriptor") {
            BitcoinUtils.isFullDescriptor("wpkh([73c5da0a/84'/0'/0']xpub/0/*)") shouldBe true
        }

        test("pkh() is a full descriptor") {
            BitcoinUtils.isFullDescriptor("pkh([fp]xpub/0/*)") shouldBe true
        }

        test("tr() is a full descriptor") {
            BitcoinUtils.isFullDescriptor("tr([fp]xpub/0/*)") shouldBe true
        }

        test("sh(wpkh()) is NOT a supported full descriptor") {
            BitcoinUtils.isFullDescriptor("sh(wpkh([fp]xpub/0/*))") shouldBe false
        }

        test("sh() is NOT a supported full descriptor") {
            BitcoinUtils.isFullDescriptor("sh(something)") shouldBe false
        }

        test("case-insensitive") {
            BitcoinUtils.isFullDescriptor("WPKH([fp]xpub/0/*)") shouldBe true
        }

        test("bare xpub is NOT a full descriptor") {
            BitcoinUtils.isFullDescriptor("xpub6ABC123") shouldBe false
        }

        test("origin-prefixed xpub is NOT a full descriptor") {
            BitcoinUtils.isFullDescriptor("[73c5da0a/84'/0'/0']xpub6ABC") shouldBe false
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // isBip389Multipath / isBip389Reversed — BIP 389 detection.
    // ══════════════════════════════════════════════════════════════════

    context("BIP 389 multipath detection") {

        test("detects <0;1> as multipath") {
            BitcoinUtils.isBip389Multipath("wpkh([fp]xpub/<0;1>/*)") shouldBe true
        }

        test("detects <1;0> as multipath") {
            BitcoinUtils.isBip389Multipath("wpkh([fp]xpub/<1;0>/*)") shouldBe true
        }

        test("regular descriptor is not multipath") {
            BitcoinUtils.isBip389Multipath("wpkh([fp]xpub/0/*)") shouldBe false
        }

        test("<0;1> is not reversed") {
            BitcoinUtils.isBip389Reversed("wpkh([fp]xpub/<0;1>/*)") shouldBe false
        }

        test("<1;0> is reversed") {
            BitcoinUtils.isBip389Reversed("wpkh([fp]xpub/<1;0>/*)") shouldBe true
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // deriveDescriptorPair — splits a single descriptor into ext/int.
    // A bug means change addresses go to wrong derivation path.
    // ══════════════════════════════════════════════════════════════════

    context("deriveDescriptorPair") {

        test("descriptor with /0/*) -> external stays, internal replaces to /1/*)") {
            val (ext, int) = BitcoinUtils.deriveDescriptorPair("wpkh([fp]xpub/0/*)")
            ext shouldBe "wpkh([fp]xpub/0/*)"
            int shouldBe "wpkh([fp]xpub/1/*)"
        }

        test("descriptor with /1/*) -> external replaces to /0/*), internal stays") {
            val (ext, int) = BitcoinUtils.deriveDescriptorPair("wpkh([fp]xpub/1/*)")
            ext shouldBe "wpkh([fp]xpub/0/*)"
            int shouldBe "wpkh([fp]xpub/1/*)"
        }

        test("descriptor without child path -> appends /0/* and /1/*") {
            val (ext, int) = BitcoinUtils.deriveDescriptorPair("wpkh([fp]xpub)")
            ext shouldBe "wpkh([fp]xpub/0/*)"
            int shouldBe "wpkh([fp]xpub/1/*)"
        }

        test("strips checksum before deriving") {
            val (ext, int) = BitcoinUtils.deriveDescriptorPair("wpkh([fp]xpub/0/*)#abcdef12")
            ext shouldBe "wpkh([fp]xpub/0/*)"
            int shouldBe "wpkh([fp]xpub/1/*)"
        }

        test("pkh descriptor with /0/*)") {
            val (ext, int) = BitcoinUtils.deriveDescriptorPair("pkh([fp]xpub/0/*)")
            ext shouldBe "pkh([fp]xpub/0/*)"
            int shouldBe "pkh([fp]xpub/1/*)"
        }

        test("tr descriptor with /0/*)") {
            val (ext, int) = BitcoinUtils.deriveDescriptorPair("tr([fp]xpub/0/*)")
            ext shouldBe "tr([fp]xpub/0/*)"
            int shouldBe "tr([fp]xpub/1/*)"
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // inputWeightWU — reference input weights per address type.
    // A bug means wrong fee estimation for watch-only wallets.
    // ══════════════════════════════════════════════════════════════════

    context("inputWeightWU") {

        test("LEGACY = 592 WU (P2PKH)") {
            BitcoinUtils.inputWeightWU(AddressType.LEGACY) shouldBe 592L
        }

        test("SEGWIT = 272 WU (P2WPKH)") {
            BitcoinUtils.inputWeightWU(AddressType.SEGWIT) shouldBe 272L
        }

        test("TAPROOT = 230 WU (P2TR)") {
            BitcoinUtils.inputWeightWU(AddressType.TAPROOT) shouldBe 230L
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // estimateVsizeFromComponents — weight estimation for watch-only.
    // A bug means paying wrong fees when hardware wallet can't sign.
    // ══════════════════════════════════════════════════════════════════

    context("estimateVsizeFromComponents") {

        test("typical P2WPKH: 1 input, 2 outputs (P2WPKH + P2WPKH change)") {
            // overhead = 42 WU (segwit)
            // 1 input * 272 WU = 272 WU
            // 2 outputs: (9+22)*4 = 124 each = 248 WU
            // total = 42 + 272 + 248 = 562 WU -> ceil(562/4) = 141 vB
            val vsize = BitcoinUtils.estimateVsizeFromComponents(
                numInputs = 1,
                inputWeightWU = 272L,
                outputScriptLengths = listOf(22, 22),
                hasWitness = true,
            )
            vsize shouldBe 141.0
        }

        test("typical P2PKH: 1 input, 2 outputs (P2PKH + P2PKH change)") {
            // overhead = 40 WU (non-segwit, inputWU >= 500)
            // 1 input * 592 WU = 592 WU
            // 2 outputs: (9+25)*4 = 136 each = 272 WU
            // total = 40 + 592 + 272 = 904 WU -> ceil(904/4) = 226 vB
            val vsize = BitcoinUtils.estimateVsizeFromComponents(
                numInputs = 1,
                inputWeightWU = 592L,
                outputScriptLengths = listOf(25, 25),
                hasWitness = false,
            )
            vsize shouldBe 226.0
        }

        test("P2TR: 1 input, 2 outputs (P2TR + P2TR change)") {
            // overhead = 42 WU (segwit)
            // 1 input * 230 WU = 230 WU
            // 2 outputs: (9+34)*4 = 172 each = 344 WU
            // total = 42 + 230 + 344 = 616 WU -> ceil(616/4) = 154 vB
            val vsize = BitcoinUtils.estimateVsizeFromComponents(
                numInputs = 1,
                inputWeightWU = 230L,
                outputScriptLengths = listOf(34, 34),
                hasWitness = true,
            )
            vsize shouldBe 154.0
        }

        test("multiple inputs: 3 P2WPKH inputs, 2 outputs") {
            // overhead = 42 WU
            // 3 inputs * 272 WU = 816 WU
            // 2 outputs: 124 + 124 = 248 WU
            // total = 42 + 816 + 248 = 1106 WU -> ceil(1106/4) = 277 vB
            val vsize = BitcoinUtils.estimateVsizeFromComponents(
                numInputs = 3,
                inputWeightWU = 272L,
                outputScriptLengths = listOf(22, 22),
                hasWitness = true,
            )
            vsize shouldBe 277.0
        }

        test("mixed output types: P2WPKH input, P2TR output + P2WPKH change") {
            // overhead = 42 WU
            // 1 input * 272 WU = 272 WU
            // outputs: (9+34)*4=172 + (9+22)*4=124 = 296 WU
            // total = 42 + 272 + 296 = 610 WU -> ceil(610/4) = 153 vB
            val vsize = BitcoinUtils.estimateVsizeFromComponents(
                numInputs = 1,
                inputWeightWU = 272L,
                outputScriptLengths = listOf(34, 22),
                hasWitness = true,
            )
            vsize shouldBe 153.0
        }

        test("no outputs (sweep with no change)") {
            // This would be unusual but tests the math
            // overhead = 42, 1 input * 272 = 272, outputs = 0
            // total = 314 WU -> ceil(314/4) = 79 vB
            val vsize = BitcoinUtils.estimateVsizeFromComponents(
                numInputs = 1,
                inputWeightWU = 272L,
                outputScriptLengths = emptyList(),
                hasWitness = true,
            )
            vsize shouldBe 79.0
        }

        test("zero inputs") {
            // overhead = 42, 0 inputs, 1 output = (9+22)*4 = 124
            // total = 166 WU -> ceil(166/4) = 42 vB
            val vsize = BitcoinUtils.estimateVsizeFromComponents(
                numInputs = 0,
                inputWeightWU = 272L,
                outputScriptLengths = listOf(22),
                hasWitness = true,
            )
            vsize shouldBe 42.0
        }

        test("witness flag controls segwit overhead") {
            val vsizeSegwit = BitcoinUtils.estimateVsizeFromComponents(1, 272L, listOf(22), hasWitness = true)
            val vsizeLegacy = BitcoinUtils.estimateVsizeFromComponents(1, 592L, listOf(25), hasWitness = false)
            // Segwit overhead = 42, Legacy overhead = 40
            // The difference shows in the total
            vsizeSegwit shouldNotBe vsizeLegacy
        }

        test("CompactSize overhead grows when input count exceeds 252") {
            val vsize252 = BitcoinUtils.estimateVsizeFromComponents(252, 272L, listOf(22), hasWitness = true)
            val vsize253 = BitcoinUtils.estimateVsizeFromComponents(253, 272L, listOf(22), hasWitness = true)
            vsize253 shouldBe vsize252 + 70.0
        }

    }

    // ══════════════════════════════════════════════════════════════════
    // parseBackupJson — backup import field extraction.
    // A bug means restoring wrong wallet type or losing funds.
    // ══════════════════════════════════════════════════════════════════

    context("parseBackupJson") {

        test("full backup with mnemonic extracts all fields") {
            val wallet = org.json.JSONObject().apply {
                put("name", "My Wallet")
                put("addressType", "TAPROOT")
                put("network", "BITCOIN")
                put("seedFormat", "BIP39")
                put("derivationPath", "86'/0'/0'")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
                put("extendedPublicKey", "xpub6ABC")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.name shouldBe "My Wallet"
            result.addressType shouldBe AddressType.TAPROOT
            result.network shouldBe "BITCOIN"
            result.seedFormat shouldBe "BIP39"
            result.keyMaterial shouldBe "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            result.isWatchOnly shouldBe false
            result.customDerivationPath shouldBe "86'/0'/0'"
        }

        test("watch-only backup (no mnemonic, only xpub)") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Watch Only")
                put("addressType", "SEGWIT")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "null")
                put("extendedPublicKey", "xpub6CatLookfBzE1...")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.keyMaterial shouldBe "xpub6CatLookfBzE1..."
            result.isWatchOnly shouldBe true
        }

        test("missing addressType defaults to SEGWIT") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Default Wallet")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test mnemonic words")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.addressType shouldBe AddressType.SEGWIT
        }

        test("missing network defaults to BITCOIN") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
                put("addressType", "SEGWIT")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test words")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.network shouldBe "BITCOIN"
        }

        test("testnet backup is rejected") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
                put("addressType", "SEGWIT")
                put("network", "TESTNET")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test words")
            }

            val error =
                shouldThrow<IllegalArgumentException> {
                    BitcoinUtils.parseBackupJson(wallet, keyMaterial)
                }
            error.message shouldBe BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE
        }

        test("missing seedFormat defaults to BIP39") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
                put("addressType", "SEGWIT")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test words")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.seedFormat shouldBe "BIP39"
        }

        test("invalid addressType defaults to SEGWIT") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
                put("addressType", "INVALID_TYPE")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test words")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.addressType shouldBe AddressType.SEGWIT
        }

        test("nested SegWit addressType is rejected") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
                put("addressType", "NESTED_SEGWIT")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test words")
            }

            val error =
                shouldThrow<IllegalArgumentException> {
                    BitcoinUtils.parseBackupJson(wallet, keyMaterial)
                }
            error.message shouldBe BitcoinUtils.UNSUPPORTED_NESTED_SEGWIT_MESSAGE
        }

        test("blank mnemonic and blank xpub throws") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "")
                put("extendedPublicKey", "")
            }

            shouldThrow<IllegalStateException> {
                BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            }
        }

        test("'null' string mnemonic treated as missing") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "null")
                put("extendedPublicKey", "xpub6Something")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.isWatchOnly shouldBe true
            result.keyMaterial shouldBe "xpub6Something"
        }

        test("missing name defaults to 'Restored Wallet'") {
            val wallet = org.json.JSONObject().apply {
                put("addressType", "SEGWIT")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test words")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.name shouldBe "Restored Wallet"
        }

        test("null derivation path extracted as null") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
                put("addressType", "SEGWIT")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test words")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.customDerivationPath shouldBe null
        }

        test("'null' string derivation path treated as null") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
                put("derivationPath", "null")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "test words")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.customDerivationPath shouldBe null
        }

        test("mnemonic preferred over xpub when both present") {
            val wallet = org.json.JSONObject().apply {
                put("name", "Test")
            }
            val keyMaterial = org.json.JSONObject().apply {
                put("mnemonic", "my secret words")
                put("extendedPublicKey", "xpub6ABC")
            }

            val result = BitcoinUtils.parseBackupJson(wallet, keyMaterial)
            result.keyMaterial shouldBe "my secret words"
            result.isWatchOnly shouldBe false
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // parseFeeEstimatesJson — mempool.space JSON parsing.
    // A bug means displaying wrong fee rates to the user.
    // ══════════════════════════════════════════════════════════════════

    context("parseFeeEstimatesJson") {

        test("parses complete mempool.space recommended response") {
            val json = """{"fastestFee":25,"halfHourFee":20,"hourFee":15,"minimumFee":5}"""
            val result = BitcoinUtils.parseFeeEstimatesJson(json)
            result.fastestFee shouldBe 25.0
            result.halfHourFee shouldBe 20.0
            result.hourFee shouldBe 15.0
            result.minimumFee shouldBe 5.0
        }

        test("parses precise endpoint with decimals") {
            val json = """{"fastestFee":25.5,"halfHourFee":20.3,"hourFee":15.1,"minimumFee":5.7}"""
            val result = BitcoinUtils.parseFeeEstimatesJson(json)
            result.fastestFee shouldBe 25.5
            result.halfHourFee shouldBe 20.3
            result.hourFee shouldBe 15.1
            result.minimumFee shouldBe 5.7
        }

        test("missing fields default to 1.0") {
            val json = """{"fastestFee":10}"""
            val result = BitcoinUtils.parseFeeEstimatesJson(json)
            result.fastestFee shouldBe 10.0
            result.halfHourFee shouldBe 1.0
            result.hourFee shouldBe 1.0
            result.minimumFee shouldBe 1.0
        }

        test("empty JSON defaults all to 1.0") {
            val json = """{}"""
            val result = BitcoinUtils.parseFeeEstimatesJson(json)
            result.fastestFee shouldBe 1.0
            result.halfHourFee shouldBe 1.0
            result.hourFee shouldBe 1.0
            result.minimumFee shouldBe 1.0
        }

        test("extra fields are ignored") {
            val json = """{"fastestFee":50,"halfHourFee":30,"hourFee":20,"minimumFee":10,"economyFee":5}"""
            val result = BitcoinUtils.parseFeeEstimatesJson(json)
            result.fastestFee shouldBe 50.0
            result.minimumFee shouldBe 10.0
        }

        test("malformed JSON throws") {
            shouldThrow<org.json.JSONException> {
                BitcoinUtils.parseFeeEstimatesJson("not json")
            }
        }

        test("zero fee rates parsed correctly") {
            val json = """{"fastestFee":0,"halfHourFee":0,"hourFee":0,"minimumFee":0}"""
            val result = BitcoinUtils.parseFeeEstimatesJson(json)
            result.fastestFee shouldBe 0.0
            result.halfHourFee shouldBe 0.0
        }

        test("very large fee rates parsed correctly") {
            val json = """{"fastestFee":1000.5,"halfHourFee":500.25,"hourFee":250.125,"minimumFee":1.0}"""
            val result = BitcoinUtils.parseFeeEstimatesJson(json)
            result.fastestFee shouldBe 1000.5
            result.halfHourFee shouldBe 500.25
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Helper functions
    // ══════════════════════════════════════════════════════════════════

}) {
    companion object {
        /** Convert hex string to ByteArray */
        fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            }
            return data
        }
    }
}
