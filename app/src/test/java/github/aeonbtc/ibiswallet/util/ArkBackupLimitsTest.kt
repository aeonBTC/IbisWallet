package github.aeonbtc.ibiswallet.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArkBackupLimitsTest : FunSpec({
    test("bounded backup input rejects data beyond the cap") {
        shouldThrow<IllegalArgumentException> {
            ArkWalletDataPack.readBounded(ByteArrayInputStream(ByteArray(65)), 64)
        }
    }

    test("import ignores an archive supplied mailbox scanned marker") {
        val root = Files.createTempDirectory("ibis-ark-marker").toFile()
        try {
            val bytes = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("db.sqlite"))
                    zip.write("SQLite format 3\u0000".toByteArray() + ByteArray(32))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("ibis-mailbox-scanned"))
                    zip.write("1".toByteArray())
                    zip.closeEntry()
                }
            }.toByteArray()
            ArkWalletDataPack.unpackZip(root, bytes)
            root.resolve("ibis-mailbox-scanned").exists() shouldBe false
        } finally {
            root.deleteRecursively()
        }
    }
})
