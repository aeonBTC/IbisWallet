package github.aeonbtc.ibiswallet.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.crypto.AEADBadTagException

/**
 * Tests for CryptoUtils — AES-256-GCM + PBKDF2 encryption used for backup files.
 * A bug here means users can't restore encrypted backups or data corruption.
 *
 * Uses low iteration count (1000) for fast test execution — the PBKDF2 logic
 * is identical regardless of iteration count; production uses 600,000.
 */
class CryptoUtilsTest : FunSpec({

    val TEST_ITERATIONS = 1000 // Fast for testing; production uses 600,000

    // ══════════════════════════════════════════════════════════════════
    // Round-trip: encrypt then decrypt should return original plaintext
    // ══════════════════════════════════════════════════════════════════

    context("encrypt/decrypt round-trip") {

        test("simple text round-trip") {
            val plaintext = "Hello, Bitcoin!".toByteArray()
            val password = "correcthorsebatterystaple"

            val encrypted = CryptoUtils.encrypt(plaintext, password, TEST_ITERATIONS)
            val decrypted = CryptoUtils.decrypt(encrypted, password, TEST_ITERATIONS)

            decrypted shouldBe plaintext
        }

        test("empty plaintext round-trip") {
            val plaintext = ByteArray(0)
            val password = "password123"

            val encrypted = CryptoUtils.encrypt(plaintext, password, TEST_ITERATIONS)
            val decrypted = CryptoUtils.decrypt(encrypted, password, TEST_ITERATIONS)

            decrypted shouldBe plaintext
        }

        test("large plaintext round-trip (10 KB)") {
            val plaintext = ByteArray(10240) { (it % 256).toByte() }
            val password = "myStrongPassword"

            val encrypted = CryptoUtils.encrypt(plaintext, password, TEST_ITERATIONS)
            val decrypted = CryptoUtils.decrypt(encrypted, password, TEST_ITERATIONS)

            decrypted shouldBe plaintext
        }

        test("unicode password round-trip") {
            val plaintext = "secret data".toByteArray()
            val password = "\u00fc\u00f6\u00e4\u00df\u20ac\u00a5\u00a3" // umlaut + currency symbols

            val encrypted = CryptoUtils.encrypt(plaintext, password, TEST_ITERATIONS)
            val decrypted = CryptoUtils.decrypt(encrypted, password, TEST_ITERATIONS)

            decrypted shouldBe plaintext
        }

        test("single-char password round-trip") {
            val plaintext = "data".toByteArray()
            val password = "x"

            val encrypted = CryptoUtils.encrypt(plaintext, password, TEST_ITERATIONS)
            val decrypted = CryptoUtils.decrypt(encrypted, password, TEST_ITERATIONS)

            decrypted shouldBe plaintext
        }

        test("JSON backup content round-trip") {
            val json = """{"wallet":{"name":"My Wallet","addressType":"SEGWIT"},"keyMaterial":{"mnemonic":"abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"}}"""
            val plaintext = json.toByteArray(Charsets.UTF_8)
            val password = "backup-password-2024"

            val encrypted = CryptoUtils.encrypt(plaintext, password, TEST_ITERATIONS)
            val decrypted = CryptoUtils.decrypt(encrypted, password, TEST_ITERATIONS)

            String(decrypted, Charsets.UTF_8) shouldBe json
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Wrong password must fail — critical for security
    // ══════════════════════════════════════════════════════════════════

    context("wrong password") {

        test("wrong password throws AEADBadTagException") {
            val plaintext = "secret mnemonic words".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "correct-password", TEST_ITERATIONS)

            shouldThrow<AEADBadTagException> {
                CryptoUtils.decrypt(encrypted, "wrong-password", TEST_ITERATIONS)
            }
        }

        test("empty password vs non-empty password throws") {
            val plaintext = "data".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "password", TEST_ITERATIONS)

            shouldThrow<AEADBadTagException> {
                CryptoUtils.decrypt(encrypted, "", TEST_ITERATIONS)
            }
        }

        test("case-sensitive password: 'Password' != 'password'") {
            val plaintext = "data".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "Password", TEST_ITERATIONS)

            shouldThrow<AEADBadTagException> {
                CryptoUtils.decrypt(encrypted, "password", TEST_ITERATIONS)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Corrupted data must fail — GCM authentication tag catches tampering
    // ══════════════════════════════════════════════════════════════════

    context("corrupted data") {

        test("flipped ciphertext bit throws AEADBadTagException") {
            val plaintext = "important data".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "password", TEST_ITERATIONS)

            // Flip a bit in the ciphertext
            val corrupted = encrypted.ciphertext.copyOf()
            corrupted[0] = (corrupted[0].toInt() xor 0x01).toByte()
            val corruptedPayload = CryptoUtils.EncryptedPayload(encrypted.salt, encrypted.iv, corrupted)

            shouldThrow<AEADBadTagException> {
                CryptoUtils.decrypt(corruptedPayload, "password", TEST_ITERATIONS)
            }
        }

        test("modified IV throws AEADBadTagException") {
            val plaintext = "important data".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "password", TEST_ITERATIONS)

            val badIv = encrypted.iv.copyOf()
            badIv[0] = (badIv[0].toInt() xor 0xFF).toByte()
            val corruptedPayload = CryptoUtils.EncryptedPayload(encrypted.salt, badIv, encrypted.ciphertext)

            shouldThrow<AEADBadTagException> {
                CryptoUtils.decrypt(corruptedPayload, "password", TEST_ITERATIONS)
            }
        }

        test("modified salt produces wrong key, throws AEADBadTagException") {
            val plaintext = "important data".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "password", TEST_ITERATIONS)

            val badSalt = encrypted.salt.copyOf()
            badSalt[0] = (badSalt[0].toInt() xor 0xFF).toByte()
            val corruptedPayload = CryptoUtils.EncryptedPayload(badSalt, encrypted.iv, encrypted.ciphertext)

            shouldThrow<AEADBadTagException> {
                CryptoUtils.decrypt(corruptedPayload, "password", TEST_ITERATIONS)
            }
        }

        test("truncated ciphertext throws") {
            val plaintext = "important data".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "password", TEST_ITERATIONS)

            val truncated = encrypted.ciphertext.sliceArray(0 until encrypted.ciphertext.size / 2)
            val corruptedPayload = CryptoUtils.EncryptedPayload(encrypted.salt, encrypted.iv, truncated)

            shouldThrow<AEADBadTagException> {
                CryptoUtils.decrypt(corruptedPayload, "password", TEST_ITERATIONS)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Encryption properties — randomness, payload structure
    // ══════════════════════════════════════════════════════════════════

    context("encryption properties") {

        test("salt is 16 bytes") {
            val encrypted = CryptoUtils.encrypt("data".toByteArray(), "pass", TEST_ITERATIONS)
            encrypted.salt.size shouldBe 16
        }

        test("IV is 12 bytes") {
            val encrypted = CryptoUtils.encrypt("data".toByteArray(), "pass", TEST_ITERATIONS)
            encrypted.iv.size shouldBe 12
        }

        test("ciphertext is longer than plaintext (includes GCM tag)") {
            val plaintext = "data".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "pass", TEST_ITERATIONS)
            // GCM adds 16-byte authentication tag
            encrypted.ciphertext.size shouldBe plaintext.size + 16
        }

        test("two encryptions of same data produce different ciphertexts (random salt/IV)") {
            val plaintext = "same data".toByteArray()
            val password = "same password"
            val enc1 = CryptoUtils.encrypt(plaintext, password, TEST_ITERATIONS)
            val enc2 = CryptoUtils.encrypt(plaintext, password, TEST_ITERATIONS)

            // Salt should differ (16 random bytes)
            enc1.salt shouldNotBe enc2.salt
            // IV should differ (12 random bytes)
            enc1.iv shouldNotBe enc2.iv
            // Ciphertext should differ
            enc1.ciphertext.toList() shouldNotBe enc2.ciphertext.toList()
        }

        test("different iterations produce different keys (wrong iteration count fails decrypt)") {
            val plaintext = "data".toByteArray()
            val encrypted = CryptoUtils.encrypt(plaintext, "password", 1000)

            shouldThrow<AEADBadTagException> {
                CryptoUtils.decrypt(encrypted, "password", 2000)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // EncryptedPayload equality
    // ══════════════════════════════════════════════════════════════════

    context("EncryptedPayload equality") {

        test("identical payloads are equal") {
            val salt = ByteArray(16) { 1 }
            val iv = ByteArray(12) { 2 }
            val ciphertext = ByteArray(32) { 3 }
            val p1 = CryptoUtils.EncryptedPayload(salt, iv, ciphertext)
            val p2 = CryptoUtils.EncryptedPayload(salt.copyOf(), iv.copyOf(), ciphertext.copyOf())
            p1 shouldBe p2
        }

        test("different salt means not equal") {
            val p1 = CryptoUtils.EncryptedPayload(ByteArray(16) { 1 }, ByteArray(12), ByteArray(32))
            val p2 = CryptoUtils.EncryptedPayload(ByteArray(16) { 2 }, ByteArray(12), ByteArray(32))
            p1 shouldNotBe p2
        }
    }
})
