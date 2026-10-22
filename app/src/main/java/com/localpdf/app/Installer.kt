package com.localpdf.app

import android.content.Context
import android.os.StatFs
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

typealias ProgressCallback = (label: String, done: Long, total: Long) -> Unit

class PausedException : IOException("Paused by user")

object Config {
    const val RELEASE_BASE = "https://github.com/Zlatan-RDR/LocalPDF/releases/download/packs-v1"
}

object Installer {

    private const val OWNER_RW = 0x180
    private const val OWNER_RWX = 0x1C0
    private const val MARKER = ".localpdf-core-installed"
    private const val MAX_ATTEMPTS = 40

    @Volatile
    private var pauseRequested = false

    fun requestPause() {
        pauseRequested = true
    }

    private fun checkPause() {
        if (pauseRequested) throw PausedException()
    }

    fun rootfsDir(ctx: Context): File = File(ctx.filesDir, "rootfs")

    private fun downloadsDir(ctx: Context): File = File(ctx.filesDir, "downloads")

    fun isCoreInstalled(ctx: Context): Boolean = File(rootfsDir(ctx), MARKER).exists()

    fun savedDownloadBytes(ctx: Context): Long {
        val files = downloadsDir(ctx).listFiles() ?: return 0L
        return files.filter { it.isFile }.sumOf { it.length() }
    }

    fun clearSavedDownloads(ctx: Context) {
        deleteTree(downloadsDir(ctx))
    }

    private fun open(url: String, rangeStart: Long): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "LocalPDF/0.1.1")
        conn.setRequestProperty("Accept-Encoding", "identity")
        if (rangeStart > 0) {
            conn.setRequestProperty("Range", "bytes=$rangeStart-")
        }
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            conn.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return conn
    }

    private fun sleepChecking(seconds: Int) {
        for (i in 0 until seconds) {
            checkPause()
            Thread.sleep(1000)
        }
    }

    private fun <T> retrying(what: String, log: (String) -> Unit, block: () -> T): T {
        var attempt = 0
        while (true) {
            checkPause()
            try {
                return block()
            } catch (e: PausedException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    throw IOException("$what failed after $attempt attempts: ${e.message}", e)
                }
                val wait = minOf(3 * attempt, 20)
                log("$what interrupted (${e.javaClass.simpleName}: ${e.message}); retry $attempt in $wait s")
                sleepChecking(wait)
            }
        }
    }

    private fun fetchManifest(log: (String) -> Unit): JSONObject {
        return retrying("manifest", log) {
            val conn = open("${Config.RELEASE_BASE}/manifest.json", 0L)
            try {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }
    }

    fun installCore(ctx: Context, onProgress: ProgressCallback, log: (String) -> Unit) {
        pauseRequested = false
        val manifest = fetchManifest(log)
        val files = manifest.getJSONObject("files")
        val core = files.getJSONObject("core.tar.xz")
        val jar = files.getJSONObject("stirling-server.jar")
        log("Stirling release: ${manifest.optString("stirling_tag")}")

        val dlDir = downloadsDir(ctx)
        dlDir.mkdirs()

        val saved = savedDownloadBytes(ctx)
        val needed = core.getLong("installed_bytes") + core.getLong("bytes") +
            jar.getLong("bytes") * 2 + 200L * MB - saved
        val free = StatFs(ctx.filesDir.absolutePath).availableBytes
        if (free < needed) {
            throw IOException("Not enough storage: need ${needed / MB} MB, free ${free / MB} MB")
        }

        val t0 = System.currentTimeMillis()
        val coreFile = ensureDownloaded(
            "${Config.RELEASE_BASE}/core.tar.xz",
            core.getString("sha256"),
            core.getLong("bytes"),
            File(dlDir, "core.tar.xz"),
            "core",
            onProgress,
            log
        )
        val jarFile = ensureDownloaded(
            "${Config.RELEASE_BASE}/stirling-server.jar",
            jar.getString("sha256"),
            jar.getLong("bytes"),
            File(dlDir, "stirling-server.jar"),
            "jar",
            onProgress,
            log
        )
        val t1 = System.currentTimeMillis()
        log("Downloads ready (this session: ${(t1 - t0) / 1000} s)")

        val finalDir = rootfsDir(ctx)
        val tmpDir = File(ctx.filesDir, "rootfs.tmp")
        deleteTree(tmpDir)
        deleteTree(finalDir)
        tmpDir.mkdirs()

        extractTarXz(coreFile, tmpDir, onProgress, log, ctx)
        prepareGuestDirs(tmpDir)
        copyFile(jarFile, File(tmpDir, "opt/stirling/Stirling-PDF.jar"))
        File(tmpDir, MARKER).writeText(core.getString("sha256"))
        if (!tmpDir.renameTo(finalDir)) {
            throw IOException("Could not rename rootfs.tmp to rootfs")
        }
        deleteTree(dlDir)
        log("Extraction took ${(System.currentTimeMillis() - t1) / 1000} s")
        log("Core installed")
    }

    private fun ensureDownloaded(
        url: String,
        expectedSha: String,
        totalBytes: Long,
        dest: File,
        label: String,
        onProgress: ProgressCallback,
        log: (String) -> Unit
    ): File {
        if (dest.isFile && dest.length() == totalBytes &&
            sha256Of(dest).equals(expectedSha, ignoreCase = true)
        ) {
            log("$label: already downloaded and verified")
            onProgress(label, totalBytes, totalBytes)
            return dest
        }
        dest.delete()
        val part = File(dest.path + ".part")
        for (round in 1..2) {
            retrying("$label download", log) {
                downloadResumable(url, part, totalBytes, label, onProgress, log)
            }
            val actual = sha256Of(part)
            if (actual.equals(expectedSha, ignoreCase = true)) {
                if (!part.renameTo(dest)) {
                    throw IOException("Could not rename ${part.name}")
                }
                log("$label: download complete, checksum OK")
                return dest
            }
            log("$label: checksum mismatch (round $round), downloading again")
            part.delete()
        }
        throw IllegalStateException("Checksum mismatch for $label after two downloads")
    }

    private fun downloadResumable(
        url: String,
        part: File,
        totalBytes: Long,
        label: String,
        onProgress: ProgressCallback,
        log: (String) -> Unit
    ) {
        var existing = if (part.exists()) part.length() else 0L
        if (existing > totalBytes) {
            part.delete()
            existing = 0L
        }
        if (existing == totalBytes) {
            onProgress(label, totalBytes, totalBytes)
            return
        }
        if (existing > 0) {
            log("$label: resuming from ${existing / MB} MB")
        }
        val conn = open(url, existing)
        try {
            var append = existing > 0
            if (existing > 0 && conn.responseCode == HttpURLConnection.HTTP_OK) {
                log("$label: server ignored Range, restarting from zero")
                append = false
                existing = 0L
            }
            var done = existing
            var lastReport = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        checkPause()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - lastReport > 300_000_000L) {
                            lastReport = now
                            onProgress(label, done, totalBytes)
                        }
                    }
                }
            }
            onProgress(label, done, totalBytes)
            if (done < totalBytes) {
                throw IOException("Connection closed early at ${done / MB} of ${totalBytes / MB} MB")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return toHex(digest.digest())
    }

    private fun extractTarXz(
        archive: File,
        root: File,
        onProgress: ProgressCallback,
        log: (String) -> Unit,
        ctx: Context
    ) {
        val total = archive.length()
        var extracted = 0L
        var skipped = 0L
        FileInputStream(archive).use { fis ->
            val counting = CountingStream(fis) { done -> onProgress("extract", done, total) }
            val xz = XZInputStream(BufferedInputStream(counting, 256 * 1024))
            val tar = TarArchiveInputStream(xz)
            while (true) {
                checkPause()
                val entry = tar.nextTarEntry ?: break
                if (extractEntry(tar, entry, root, log)) extracted++ else skipped++
            }
        }
        log("extract: $extracted entries, skipped $skipped")
    }

    private fun extractEntry(
        tar: TarArchiveInputStream,
        entry: TarArchiveEntry,
        root: File,
        log: (String) -> Unit
    ): Boolean {
        val name = normalize(entry.name) ?: return false
        val out = File(root, name)
        val mode = entry.mode and 0xFFF
        when {
            entry.isDirectory -> {
                out.mkdirs()
                Os.chmod(out.path, mode or OWNER_RWX)
            }
            entry.isSymbolicLink -> {
                out.parentFile?.mkdirs()
                Files.deleteIfExists(out.toPath())
                Os.symlink(entry.linkName, out.path)
            }
            entry.isLink -> {
                out.parentFile?.mkdirs()
                val target = File(root, normalize(entry.linkName) ?: return false)
                if (!target.isFile) {
                    log("WARN hard link target missing: ${entry.name}")
                    return false
                }
                Files.deleteIfExists(out.toPath())
                target.copyTo(out, overwrite = true)
                Os.chmod(out.path, mode or OWNER_RW)
            }
            entry.isFile -> {
                out.parentFile?.mkdirs()
                Files.deleteIfExists(out.toPath())
                FileOutputStream(out).use { os -> tar.copyTo(os, 64 * 1024) }
                Os.chmod(out.path, mode or OWNER_RW)
            }
            else -> return false
        }
        return true
    }

    private fun normalize(raw: String): String? {
        var n = raw
        while (n.startsWith("./")) n = n.removePrefix("./")
        n = n.trimStart('/').trimEnd('/')
        if (n.isEmpty() || n == ".") return null
        if (n.split('/').any { it == ".." }) return null
        return n
    }

    private fun prepareGuestDirs(root: File) {
        for (d in listOf("dev", "proc", "sys", "tmp", "root", "work", "data", "opt/stirling", "etc")) {
            File(root, d).mkdirs()
        }
        Os.chmod(File(root, "tmp").path, 0x3FF)
        File(root, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
    }

    private fun copyFile(src: File, dst: File) {
        dst.parentFile?.mkdirs()
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output -> input.copyTo(output, 256 * 1024) }
        }
        Os.chmod(dst.path, OWNER_RW)
    }

    fun deleteTree(dir: File) {
        val start = dir.toPath()
        if (!Files.exists(start, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(d: Path, exc: IOException?): FileVisitResult {
                Files.delete(d)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private class CountingStream(
        input: InputStream,
        private val onBytes: (Long) -> Unit
    ) : FilterInputStream(input) {

        private var total = 0L
        private var lastReport = 0L

        private fun account(n: Int) {
            total += n
            val now = System.nanoTime()
            if (now - lastReport > 300_000_000L) {
                lastReport = now
                onBytes(total)
            }
        }

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) account(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) account(n)
            return n
        }
    }
}
