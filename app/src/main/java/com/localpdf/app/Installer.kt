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

object Config {
    const val RELEASE_BASE = "https://github.com/Zlatan-RDR/LocalPDF/releases/download/packs-v1"
}

object Installer {

    private const val OWNER_RW = 0x180
    private const val OWNER_RWX = 0x1C0
    private const val MARKER = ".localpdf-core-installed"

    fun rootfsDir(ctx: Context): File = File(ctx.filesDir, "rootfs")

    fun isCoreInstalled(ctx: Context): Boolean = File(rootfsDir(ctx), MARKER).exists()

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "LocalPDF/0.1.0")
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return conn
    }

    private fun fetchManifest(): JSONObject {
        val conn = open("${Config.RELEASE_BASE}/manifest.json")
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    fun installCore(ctx: Context, onProgress: ProgressCallback, log: (String) -> Unit) {
        val manifest = fetchManifest()
        val files = manifest.getJSONObject("files")
        val core = files.getJSONObject("core.tar.xz")
        val jar = files.getJSONObject("stirling-server.jar")
        log("Stirling release: ${manifest.optString("stirling_tag")}")

        val free = StatFs(ctx.filesDir.absolutePath).availableBytes
        val needed = core.getLong("installed_bytes") + jar.getLong("bytes") + 100L * MB
        if (free < needed) {
            throw IOException("Not enough storage: need ${needed / MB} MB, free ${free / MB} MB")
        }

        val finalDir = rootfsDir(ctx)
        val tmpDir = File(ctx.filesDir, "rootfs.tmp")
        deleteTree(tmpDir)
        deleteTree(finalDir)
        tmpDir.mkdirs()

        val started = System.currentTimeMillis()
        extractTarXz(
            "${Config.RELEASE_BASE}/core.tar.xz",
            core.getString("sha256"),
            core.getLong("bytes"),
            tmpDir,
            "core",
            onProgress,
            log
        )
        prepareGuestDirs(tmpDir)

        downloadFile(
            "${Config.RELEASE_BASE}/stirling-server.jar",
            jar.getString("sha256"),
            jar.getLong("bytes"),
            File(tmpDir, "opt/stirling/Stirling-PDF.jar"),
            "jar",
            onProgress
        )

        File(tmpDir, MARKER).writeText(core.getString("sha256"))
        if (!tmpDir.renameTo(finalDir)) {
            throw IOException("Could not rename rootfs.tmp to rootfs")
        }
        log("Core installed in ${(System.currentTimeMillis() - started) / 1000} s")
    }

    private fun extractTarXz(
        url: String,
        expectedSha: String,
        totalBytes: Long,
        root: File,
        label: String,
        onProgress: ProgressCallback,
        log: (String) -> Unit
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(url)
        var extracted = 0L
        var skipped = 0L
        try {
            val hashing = HashingStream(conn.inputStream, digest) { done ->
                onProgress(label, done, totalBytes)
            }
            val buffered = BufferedInputStream(hashing, 256 * 1024)
            val xz = XZInputStream(buffered)
            val tar = TarArchiveInputStream(xz)
            while (true) {
                val entry = tar.nextTarEntry ?: break
                if (extractEntry(tar, entry, root, log)) extracted++ else skipped++
            }
            val sink = ByteArray(64 * 1024)
            while (buffered.read(sink) >= 0) {
                // drain the rest so the checksum covers the whole file
            }
        } finally {
            conn.disconnect()
        }
        val actual = toHex(digest.digest())
        if (!actual.equals(expectedSha, ignoreCase = true)) {
            throw IOException("SHA-256 mismatch for $label: expected $expectedSha, got $actual")
        }
        log("$label: extracted $extracted entries, skipped $skipped, checksum OK")
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

    private fun downloadFile(
        url: String,
        expectedSha: String,
        totalBytes: Long,
        dest: File,
        label: String,
        onProgress: ProgressCallback
    ) {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(url)
        try {
            val input = HashingStream(conn.inputStream, digest) { done ->
                onProgress(label, done, totalBytes)
            }
            input.use { stream ->
                FileOutputStream(part).use { out -> stream.copyTo(out, 256 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
        val actual = toHex(digest.digest())
        if (!actual.equals(expectedSha, ignoreCase = true)) {
            part.delete()
            throw IOException("SHA-256 mismatch for $label")
        }
        if (!part.renameTo(dest)) {
            throw IOException("Could not rename ${part.name}")
        }
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

    private class HashingStream(
        input: InputStream,
        private val digest: MessageDigest,
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
            if (b >= 0) {
                digest.update(b.toByte())
                account(1)
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                digest.update(b, off, n)
                account(n)
            } else if (n < 0) {
                onBytes(total)
            }
            return n
        }

        override fun skip(n: Long): Long {
            val buf = ByteArray(8192)
            var remaining = n
            var skipped = 0L
            while (remaining > 0) {
                val r = read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (r < 0) break
                skipped += r
                remaining -= r
            }
            return skipped
        }

        override fun markSupported(): Boolean = false
    }
}
