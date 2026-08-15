package github.aeonbtc.ibiswallet.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ArkBackupCryptoTest : FunSpec({

    val seedA = ByteArray(64) { (it + 1).toByte() }
    val seedB = ByteArray(64) { (it + 99).toByte() }

    context("encrypt/decrypt round-trip") {
        test("zip-like payload round-trips") {
            val plaintext = ByteArray(4096) { (it % 251).toByte() }
            plaintext[0] = 'P'.code.toByte()
            plaintext[1] = 'K'.code.toByte()
            plaintext[2] = 3
            plaintext[3] = 4

            val encrypted = ArkBackupCrypto.encrypt(plaintext, seedA)
            ArkBackupCrypto.isEncrypted(encrypted) shouldBe true
            ArkWalletDataPack.isZipMagic(encrypted) shouldBe false

            val decrypted = ArkBackupCrypto.decrypt(encrypted, seedA)
            decrypted shouldBe plaintext
        }

        test("empty plaintext round-trips") {
            val encrypted = ArkBackupCrypto.encrypt(ByteArray(0), seedA)
            ArkBackupCrypto.decrypt(encrypted, seedA) shouldBe ByteArray(0)
        }

        test("different seeds produce different ciphertexts") {
            val plaintext = "ark-db-snapshot".toByteArray()
            val a = ArkBackupCrypto.encrypt(plaintext, seedA)
            val b = ArkBackupCrypto.encrypt(plaintext, seedB)
            a shouldNotBe b
        }

        test("same seed decrypts independently encrypted copies") {
            val plaintext = "same-seed".toByteArray()
            val a = ArkBackupCrypto.encrypt(plaintext, seedA)
            val b = ArkBackupCrypto.encrypt(plaintext, seedA)
            // Random nonces → different ciphertext
            a shouldNotBe b
            ArkBackupCrypto.decrypt(a, seedA) shouldBe plaintext
            ArkBackupCrypto.decrypt(b, seedA) shouldBe plaintext
        }
    }

    context("wrong wallet / corruption") {
        test("wrong seed throws WrongWalletException") {
            val encrypted = ArkBackupCrypto.encrypt("secret".toByteArray(), seedA)
            shouldThrow<ArkBackupCrypto.WrongWalletException> {
                ArkBackupCrypto.decrypt(encrypted, seedB)
            }
        }

        test("truncated payload throws") {
            val encrypted = ArkBackupCrypto.encrypt("data".toByteArray(), seedA)
            shouldThrow<ArkBackupCrypto.InvalidPayloadException> {
                ArkBackupCrypto.decrypt(encrypted.copyOf(10), seedA)
            }
        }

        test("tampered ciphertext throws WrongWalletException") {
            val encrypted = ArkBackupCrypto.encrypt("data".toByteArray(), seedA)
            encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0xff).toByte()
            shouldThrow<ArkBackupCrypto.WrongWalletException> {
                ArkBackupCrypto.decrypt(encrypted, seedA)
            }
        }

        test("plaintext zip is not encrypted") {
            val zip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0)
            ArkBackupCrypto.isEncrypted(zip) shouldBe false
        }
    }

    context("unwrapIfEncrypted") {
        test("decrypts encrypted payload") {
            val plain = "hello".toByteArray()
            val enc = ArkBackupCrypto.encrypt(plain, seedA)
            ArkBackupCrypto.unwrapIfEncrypted(enc, seedA) shouldBe plain
        }

        test("passes through legacy plaintext") {
            val plain = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)
            ArkBackupCrypto.unwrapIfEncrypted(plain, seedA) shouldBe plain
        }
    }

    context("seed fingerprint") {
        test("stable for same seed") {
            ArkBackupCrypto.seedFingerprint(seedA) shouldBe ArkBackupCrypto.seedFingerprint(seedA)
        }

        test("differs across seeds") {
            ArkBackupCrypto.seedFingerprint(seedA) shouldNotBe ArkBackupCrypto.seedFingerprint(seedB)
        }
    }
})

class ArkWalletDataPackTest : FunSpec({

    context("manifest") {
        test("json round-trip") {
            val m =
                ArkWalletDataPack.Manifest(
                    seedFingerprint = "abcd1234",
                    walletId = "wallet-1",
                    movementCount = 3,
                    maxMovementId = 42,
                    spendableSats = 1000L,
                    chainTipHeight = 900_000L,
                    createdAtMs = 1_700_000_000_000L,
                )
            val parsed = ArkWalletDataPack.Manifest.fromJson(m.toJson())
            parsed shouldBe m
        }
    }

    context("atomic install") {
        test("failed unpack leaves original intact") {
            val root = createTempDir(prefix = "ark-pack-")
            try {
                val target = java.io.File(root, "wallet")
                target.mkdirs()
                val marker = java.io.File(target, "db.sqlite")
                // Minimal valid-looking sqlite header so isValidSqliteFile would pass if we kept it
                marker.writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(100))
                val original = marker.readBytes()

                val badZip = byteArrayOf(1, 2, 3, 4) // not a zip
                shouldThrow<Exception> {
                    ArkWalletDataPack.installAtomically(target, badZip)
                }
                marker.exists() shouldBe true
                marker.readBytes() shouldBe original
            } finally {
                root.deleteRecursively()
            }
        }

        test("valid zip installs db.sqlite") {
            val root = createTempDir(prefix = "ark-pack-ok-")
            try {
                val target = java.io.File(root, "wallet")
                val dbBytes =
                    "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(64) { 7 }
                val zip =
                    java.io.ByteArrayOutputStream().use { baos ->
                        java.util.zip.ZipOutputStream(baos).use { zos ->
                            zos.putNextEntry(java.util.zip.ZipEntry("db.sqlite"))
                            zos.write(dbBytes)
                            zos.closeEntry()
                        }
                        baos.toByteArray()
                    }
                ArkWalletDataPack.installAtomically(target, zip)
                val installed = java.io.File(target, "db.sqlite")
                installed.isFile shouldBe true
                installed.readBytes() shouldBe dbBytes
            } finally {
                root.deleteRecursively()
            }
        }
    }

    context("zip helpers") {
        test("isZipMagic detects PK headers") {
            ArkWalletDataPack.isZipMagic(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)) shouldBe true
            ArkWalletDataPack.isZipMagic(byteArrayOf(0, 1, 2, 3)) shouldBe false
        }

        test("readManifest from zip") {
            val manifest =
                ArkWalletDataPack.Manifest(
                    seedFingerprint = "fp",
                    walletId = "id",
                    movementCount = 1,
                    maxMovementId = 1,
                    spendableSats = 10,
                    chainTipHeight = 1,
                    createdAtMs = 2,
                )
            val dbBytes =
                "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(32)
            val zip =
                java.io.ByteArrayOutputStream().use { baos ->
                    java.util.zip.ZipOutputStream(baos).use { zos ->
                        zos.putNextEntry(java.util.zip.ZipEntry(ArkWalletDataPack.MANIFEST_ENTRY))
                        zos.write(manifest.toJson().toByteArray())
                        zos.closeEntry()
                        zos.putNextEntry(java.util.zip.ZipEntry("db.sqlite"))
                        zos.write(dbBytes)
                        zos.closeEntry()
                    }
                    baos.toByteArray()
                }
            ArkWalletDataPack.readManifest(zip) shouldBe manifest
            ArkWalletDataPack.isValidZipStructure(zip) shouldBe true
        }

        test("zipDirectory embeds and readHistoryJson restores history sidecar") {
            val tmp =
                java.io.File.createTempFile("ark-pack", null).apply {
                    delete()
                    mkdirs()
                    deleteOnExit()
                }
            try {
                val db =
                    java.io.File(tmp, ArkWalletDataPack.DB_FILE_NAME).apply {
                        writeBytes(
                            "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(32),
                        )
                    }
                db.deleteOnExit()
                val history = """[{"id":7,"status":"completed","effectiveBalanceSats":1000}]"""
                val zip =
                    ArkWalletDataPack.zipDirectory(
                        dir = tmp,
                        historyJson = history,
                    )
                zip shouldNotBe null
                ArkWalletDataPack.readHistoryJson(zip!!) shouldBe history
                // History must not be unpacked into Bark datadir.
                val unpackDir =
                    java.io.File.createTempFile("ark-unpack", null).apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                try {
                    ArkWalletDataPack.unpackZip(unpackDir, zip)
                    java.io.File(unpackDir, ArkWalletDataPack.HISTORY_ENTRY).exists() shouldBe false
                    java.io.File(unpackDir, ArkWalletDataPack.DB_FILE_NAME).isFile shouldBe true
                } finally {
                    unpackDir.deleteRecursively()
                }
            } finally {
                tmp.deleteRecursively()
            }
        }
    }
})
