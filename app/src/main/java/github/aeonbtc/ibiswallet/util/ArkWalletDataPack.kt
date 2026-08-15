package github.aeonbtc.ibiswallet.util

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pack/unpack helpers for Ark (Bark) per-wallet data directories.
 * Handles zip-slip, SQLite presence checks, atomic install, and backup manifests.
 */
object ArkWalletDataPack {
    const val MANIFEST_ENTRY = "ibis-ark-manifest.json"
    /**
     * Ibis movement history JSON array. Not part of Bark's DB — mailbox recovery only
     * restores VTXOs. Written beside db.sqlite in export/auto-backup zips and restored
     * into SecureStorage on import.
     */
    const val HISTORY_ENTRY = "ibis-ark-history.json"
    const val DB_FILE_NAME = "db.sqlite"
    private val SQLITE_HEADER = "SQLite format 3".toByteArray(Charsets.US_ASCII)

    data class Manifest(
        val version: Int = 1,
        val seedFingerprint: String,
        val walletId: String,
        val movementCount: Int,
        val maxMovementId: Int,
        val spendableSats: Long,
        val chainTipHeight: Long,
        val createdAtMs: Long,
    ) {
        fun toJson(): String =
            JSONObject()
                .put("version", version)
                .put("seedFingerprint", seedFingerprint)
                .put("walletId", walletId)
                .put("movementCount", movementCount)
                .put("maxMovementId", maxMovementId)
                .put("spendableSats", spendableSats)
                .put("chainTipHeight", chainTipHeight)
                .put("createdAtMs", createdAtMs)
                .toString()

        companion object {
            fun fromJson(raw: String): Manifest? =
                runCatching {
                    val o = JSONObject(raw)
                    Manifest(
                        version = o.optInt("version", 1),
                        seedFingerprint = o.optString("seedFingerprint", ""),
                        walletId = o.optString("walletId", ""),
                        movementCount = o.optInt("movementCount", 0),
                        maxMovementId = o.optInt("maxMovementId", 0),
                        spendableSats = o.optLong("spendableSats", 0L),
                        chainTipHeight = o.optLong("chainTipHeight", 0L),
                        createdAtMs = o.optLong("createdAtMs", 0L),
                    )
                }.getOrNull()
        }
    }

    fun zipDirectory(
        dir: File,
        manifest: Manifest? = null,
        /** JSON array of ArkMovement objects (Ibis history sidecar). */
        historyJson: String? = null,
    ): ByteArray? {
        if (!dir.isDirectory) return null
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty() && manifest == null && historyJson.isNullOrBlank()) return null
        return try {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                if (manifest != null) {
                    zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zos.write(manifest.toJson().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                val history = historyJson?.takeIf { it.isNotBlank() }
                if (history != null) {
                    zos.putNextEntry(ZipEntry(HISTORY_ENTRY))
                    zos.write(history.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                for (file in files) {
                    val entryName = file.relativeTo(dir).invariantSeparatorsPath
                    if (entryName == MANIFEST_ENTRY || entryName == HISTORY_ENTRY) continue
                    if (entryName.endsWith("/$MANIFEST_ENTRY") || entryName.endsWith("/$HISTORY_ENTRY")) {
                        continue
                    }
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    fun readManifest(zipBytes: ByteArray): Manifest? {
        return runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isMetaEntry(entry.name, MANIFEST_ENTRY)) {
                        val text = zis.readBytes().toString(Charsets.UTF_8)
                        zis.closeEntry()
                        return@use Manifest.fromJson(text)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                null
            }
        }.getOrNull()
    }

    /** Ibis movement history JSON from an export/auto-backup zip, if present. */
    fun readHistoryJson(zipBytes: ByteArray): String? {
        return runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isMetaEntry(entry.name, HISTORY_ENTRY)) {
                        val text = zis.readBytes().toString(Charsets.UTF_8)
                        zis.closeEntry()
                        return@use text.takeIf { it.isNotBlank() }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                null
            }
        }.getOrNull()
    }

    private fun isMetaEntry(
        entryName: String,
        fileName: String,
    ): Boolean =
        entryName == fileName ||
            entryName.endsWith("/$fileName") ||
            entryName.substringAfterLast('/') == fileName

    fun unpackZip(
        dir: File,
        zipBytes: ByteArray,
    ) {
        dir.mkdirs()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // Manifest / history are Ibis metadata only — not Bark files.
                    if (
                        isMetaEntry(entry.name, MANIFEST_ENTRY) ||
                        isMetaEntry(entry.name, HISTORY_ENTRY)
                    ) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    val outFile = File(dir, entry.name)
                    val canonicalOut = outFile.canonicalPath
                    val root = dir.canonicalPath
                    if (!canonicalOut.startsWith(root + File.separator) && canonicalOut != root) {
                        error("Invalid ark backup path")
                    }
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val db = File(dir, DB_FILE_NAME)
        if (!db.isFile) {
            error("Ark data zip missing db.sqlite")
        }
        if (!isValidSqliteFile(db)) {
            error("Ark data zip has corrupt db.sqlite")
        }
    }

    fun isValidSqliteFile(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size + 1) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(SQLITE_HEADER.size)
                if (input.read(header) != header.size) return@use false
                header.contentEquals(SQLITE_HEADER)
            }
        }.getOrDefault(false)
    }

    fun isValidZipStructure(bytes: ByteArray): Boolean {
        if (bytes.size < 22) return false
        if (!isZipMagic(bytes)) return false
        return runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entries = 0
                while (zis.nextEntry != null) {
                    entries++
                    zis.closeEntry()
                    if (entries > 0) return@use true
                }
                false
            }
        }.getOrDefault(false)
    }

    fun isZipMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            (
                (bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) ||
                    (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()) ||
                    (bytes[2] == 7.toByte() && bytes[3] == 8.toByte())
            )
    }

    /**
     * Unpack [zipBytes] into [targetDir] atomically:
     * unpack to temp → validate → swap (keeping a pre-import copy until success).
     * On any failure the original [targetDir] is left intact (or restored).
     */
    fun installAtomically(
        targetDir: File,
        zipBytes: ByteArray,
    ) {
        val parent = targetDir.parentFile ?: error("Invalid ark data path")
        parent.mkdirs()
        val tempDir = File(parent, "${targetDir.name}.import-tmp-${System.nanoTime()}")
        val preImportDir = File(parent, "${targetDir.name}.pre-import-${System.nanoTime()}")
        try {
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()
            unpackZip(tempDir, zipBytes)

            val hadExisting = targetDir.exists()
            if (hadExisting) {
                if (preImportDir.exists()) preImportDir.deleteRecursively()
                if (!targetDir.renameTo(preImportDir)) {
                    // Fallback copy+delete when rename fails across filesystems.
                    copyDir(targetDir, preImportDir)
                    targetDir.deleteRecursively()
                }
            }
            if (!tempDir.renameTo(targetDir)) {
                copyDir(tempDir, targetDir)
                tempDir.deleteRecursively()
            }
            // Success — drop the previous copy.
            if (preImportDir.exists()) {
                preImportDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // Best-effort restore of the previous dir if we already moved it aside.
            if (preImportDir.exists() && !targetDir.exists()) {
                runCatching {
                    if (!preImportDir.renameTo(targetDir)) {
                        copyDir(preImportDir, targetDir)
                        preImportDir.deleteRecursively()
                    }
                }
            } else if (preImportDir.exists() && targetDir.exists()) {
                // Partial target — prefer previous.
                runCatching { targetDir.deleteRecursively() }
                runCatching {
                    if (!preImportDir.renameTo(targetDir)) {
                        copyDir(preImportDir, targetDir)
                        preImportDir.deleteRecursively()
                    }
                }
            }
            runCatching { if (tempDir.exists()) tempDir.deleteRecursively() }
            throw e
        } finally {
            runCatching { if (tempDir.exists()) tempDir.deleteRecursively() }
            runCatching { if (preImportDir.exists()) preImportDir.deleteRecursively() }
        }
    }

    private fun copyDir(
        from: File,
        to: File,
    ) {
        to.mkdirs()
        from.walkTopDown().forEach { src ->
            val rel = src.relativeTo(from)
            val dst = File(to, rel.path)
            if (src.isDirectory) {
                dst.mkdirs()
            } else {
                dst.parentFile?.mkdirs()
                src.inputStream().use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
