package com.localpdf.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class RunResult(
    val ok: Boolean,
    val code: Int,
    val message: String,
    val file: File?,
    val fileName: String,
    val mime: String,
    val bytes: Long,
    val millis: Long
)

object ApiClient {

    private const val BASE = "http://127.0.0.1:8080"

    // Maximum size (in UTF-8 bytes) of the file name part of a saved result.
    // Android limits a single path component to 255 bytes; the saved name also
    // gets a timestamp prefix, so we keep a safe margin.
    private const val MAX_NAME_BYTES = 120

    // URL to PDF is done in the app: Stirling's own endpoint for it fails in this
    // build ("No current ServletRequestAttributes"), so the app downloads the page
    // and sends the HTML to the working HTML to PDF endpoint.
    private const val HTML_TO_PDF_PATH = "/api/v1/convert/html/pdf"
    private const val MAX_PAGE_BYTES = 12 * 1024 * 1024
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0 Mobile Safari/537.36"

    private class FetchedPage(val bytes: ByteArray, val url: URL, val charsetName: String?)

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getString(0)
                        if (!n.isNullOrBlank()) return n
                    }
                }
        } catch (e: Throwable) {
            // fall through
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun writeText(out: OutputStream, boundary: String, name: String, value: String) {
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(value.toByteArray(Charsets.UTF_8))
        out.write("\r\n".toByteArray(Charsets.UTF_8))
    }

    private fun formPair(name: String, value: String): String =
        URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")

    private fun writeFile(ctx: Context, out: OutputStream, boundary: String, name: String, uri: Uri) {
        val fileName = displayName(ctx, uri).replace("\"", "_")
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val head = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\n" +
            "Content-Type: $mime\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        val input = ctx.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $fileName")
        input.use { it.copyTo(out, 64 * 1024) }
        out.write("\r\n".toByteArray(Charsets.UTF_8))
    }

    private fun readLimited(input: InputStream, max: Int): String {
        val buf = ByteArray(max)
        var total = 0
        while (total < max) {
            val n = input.read(buf, total, max - total)
            if (n < 0) break
            total += n
        }
        return String(buf, 0, total, Charsets.UTF_8)
    }

    // Percent-decodes a file name. A literal '+' is kept as '+' (it is not a space
    // in a file name), so it is escaped before decoding.
    private fun percentDecode(s: String): String {
        if (!s.contains('%')) return s
        return try {
            URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (e: Throwable) {
            s
        }
    }

    private fun fileNameFromHeader(cd: String?, ctype: String): String {
        if (cd != null) {
            val star = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(cd)
            if (star != null) {
                return percentDecode(star.groupValues[1].trim())
            }
            val plain = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)
            if (plain != null) return percentDecode(plain.groupValues[1].trim())
        }
        val ext = when {
            ctype.contains("pdf") -> "pdf"
            ctype.contains("zip") -> "zip"
            ctype.contains("json") -> "json"
            ctype.contains("png") -> "png"
            ctype.contains("jpeg") -> "jpg"
            ctype.contains("csv") -> "csv"
            ctype.contains("xml") -> "xml"
            ctype.contains("text") -> "txt"
            else -> "bin"
        }
        return "result.$ext"
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    // Shortens a file name so that its UTF-8 size is at most maxBytes, keeping the
    // extension and never cutting in the middle of a character.
    private fun limitBytes(name: String, maxBytes: Int): String {
        if (name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= 10) name.substring(dot) else ""
        val base = if (ext.isEmpty()) name else name.substring(0, dot)
        val budget = maxBytes - ext.toByteArray(Charsets.UTF_8).size
        val sb = StringBuilder()
        var used = 0
        var i = 0
        while (i < base.length) {
            val cp = base.codePointAt(i)
            val chunk = String(Character.toChars(cp))
            val n = chunk.toByteArray(Charsets.UTF_8).size
            if (used + n > budget) break
            sb.append(chunk)
            used += n
            i += Character.charCount(cp)
        }
        val result = if (sb.isEmpty()) "file" else sb.toString()
        return result + ext
    }

    private fun cleanResultName(raw: String): String =
        limitBytes(safeName(raw), MAX_NAME_BYTES)

    // Runs a tool.  The run is registered as a background task: the foreground service
    // keeps the process alive when the app is left, and a "finished" notification is
    // posted when the app is not visible.
    fun run(
        ctx: Context,
        tool: Tool,
        values: Map<String, String>,
        files: Map<String, List<Uri>>
    ): RunResult {
        val title = Lang.toolName(tool)
        BackgroundWork.begin(ctx, title)
        var result: RunResult? = null
        try {
            val r = runRequest(ctx, tool, values, files)
            result = r
            return r
        } finally {
            val r = result
            val ok = r != null && r.ok
            val text = when {
                r == null -> Lang.t(
                    "توقفت المهمة بخطأ غير متوقع",
                    "The task stopped with an unexpected error"
                )
                r.ok -> r.fileName + " (" + (r.bytes / 1024) + " KB)"
                else -> r.message.take(160)
            }
            BackgroundWork.end(ctx, ok, title, text)
        }
    }

    // Reads the response of a finished request and saves the result file.
    private fun readResult(ctx: Context, conn: HttpURLConnection, started: Long): RunResult {
        val code = conn.responseCode
        val ctype = conn.contentType ?: "application/octet-stream"
        if (code >= 400) {
            val stream = conn.errorStream ?: conn.inputStream
            val body = stream?.use { readLimited(it, 4000) } ?: ""
            return RunResult(
                false, code, "HTTP $code: $body", null, "", ctype, 0,
                System.currentTimeMillis() - started
            )
        }

        val name = cleanResultName(
            fileNameFromHeader(conn.getHeaderField("Content-Disposition"), ctype)
        )
        val dir = File(ctx.filesDir, "results")
        dir.mkdirs()
        val outFile = File(dir, "${System.currentTimeMillis()}_$name")
        conn.inputStream.use { input ->
            outFile.outputStream().use { o -> input.copyTo(o, 256 * 1024) }
        }
        return RunResult(
            true, code, "OK", outFile, name,
            ctype.substringBefore(';').trim(), outFile.length(),
            System.currentTimeMillis() - started
        )
    }

    private fun readAllLimited(input: InputStream, max: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (bos.size() + n > max) {
                throw IOException("The page is larger than " + (max / 1024 / 1024) + " MB")
            }
            bos.write(buf, 0, n)
        }
        return bos.toByteArray()
    }

    // Downloads a web page, following redirects by hand (also from http to https).
    private fun fetchPage(start: URL): FetchedPage {
        var current = start
        var redirects = 0
        while (true) {
            val c = current.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", BROWSER_UA)
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                c.setRequestProperty("Accept-Language", "ar,en;q=0.8")
                val code = c.responseCode
                val location = c.getHeaderField("Location")
                if (code in 300..399 && location != null) {
                    redirects++
                    if (redirects > 8) throw IOException("Too many redirects")
                    val next = URL(current, location)
                    if (next.protocol != "http" && next.protocol != "https") {
                        throw IOException("Unsupported redirect to " + next.protocol)
                    }
                    current = next
                } else {
                    if (code >= 400) throw IOException("HTTP $code from the website")
                    val ctype = c.contentType ?: ""
                    val cs = Regex("charset=\"?([A-Za-z0-9_.:-]+)\"?", RegexOption.IGNORE_CASE)
                        .find(ctype)?.groupValues?.get(1)
                    val bytes = c.inputStream.use { readAllLimited(it, MAX_PAGE_BYTES) }
                    return FetchedPage(bytes, current, cs)
                }
            } finally {
                c.disconnect()
            }
        }
    }

    private fun runUrlToPdf(ctx: Context, values: Map<String, String>): RunResult {
        val started = System.currentTimeMillis()
        fun fail(code: Int, msg: String): RunResult =
            RunResult(false, code, msg, null, "", "", 0, System.currentTimeMillis() - started)

        var raw = values["urlInput"].orEmpty().trim()
        if (raw.isEmpty()) return fail(-1, "The link is empty")
        if (!raw.contains("://")) raw = "https://" + raw
        val start = try {
            URL(raw)
        } catch (e: Throwable) {
            return fail(-1, "Invalid link: " + raw)
        }
        if (start.protocol != "http" && start.protocol != "https") {
            return fail(-1, "Only http and https links are supported")
        }
        val page = try {
            fetchPage(start)
        } catch (e: IOException) {
            return fail(-1, "Could not fetch the page: " + e.message)
        }
        val text = try {
            String(page.bytes, charset(page.charsetName ?: "UTF-8"))
        } catch (e: Throwable) {
            String(page.bytes, Charsets.UTF_8)
        }
        val html = text.toByteArray(Charsets.UTF_8)
        val host = page.url.host
        val fileName = safeName(if (host.isNullOrBlank()) "page" else host) + ".html"
        val boundary = "----LocalPDF" + System.currentTimeMillis()
        val conn = URL(BASE + HTML_TO_PDF_PATH).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15 * 60 * 1000
            conn.setChunkedStreamingMode(256 * 1024)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("Accept", "*/*")
            conn.outputStream.use { out ->
                val head = "--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"fileInput\"; filename=\"$fileName\"\r\n" +
                    "Content-Type: text/html\r\n\r\n"
                out.write(head.toByteArray(Charsets.UTF_8))
                out.write(html)
                out.write("\r\n".toByteArray(Charsets.UTF_8))
                writeText(out, boundary, "zoom", "1")
                out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            return readResult(ctx, conn, started)
        } catch (e: IOException) {
            return fail(-1, "Connection error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun runRequest(
        ctx: Context,
        tool: Tool,
        values: Map<String, String>,
        files: Map<String, List<Uri>>
    ): RunResult {
        if (tool.path.trimEnd('/').endsWith("/convert/url/pdf")) {
            return runUrlToPdf(ctx, values)
        }
        val started = System.currentTimeMillis()
        val boundary = "----LocalPDF" + System.currentTimeMillis()
        val conn = URL(BASE + tool.path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15 * 60 * 1000
            conn.setChunkedStreamingMode(256 * 1024)
            if (tool.formEncoded) {
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            } else {
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.setRequestProperty("Accept", "*/*")

            conn.outputStream.use { out ->
                if (tool.formEncoded) {
                    val pairs = ArrayList<String>()
                    for (f in tool.fields) {
                        if (f.type == FieldType.FILE || f.type == FieldType.FILES) continue
                        if (f.type == FieldType.LIST) {
                            values[f.name].orEmpty()
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .forEach { pairs.add(formPair(f.name, it)) }
                        } else {
                            val v = values[f.name].orEmpty()
                            if (v.isNotEmpty()) pairs.add(formPair(f.name, v))
                        }
                    }
                    out.write(pairs.joinToString("&").toByteArray(Charsets.UTF_8))
                } else {
                    for (f in tool.fields) {
                        when (f.type) {
                            FieldType.FILE, FieldType.FILES -> {
                                val list = files[f.name].orEmpty()
                                val chosen = if (f.type == FieldType.FILE) list.take(1) else list
                                for (u in chosen) writeFile(ctx, out, boundary, f.name, u)
                            }
                            FieldType.LIST -> {
                                val raw = values[f.name].orEmpty()
                                raw.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .forEach { writeText(out, boundary, f.name, it) }
                            }
                            else -> {
                                val v = values[f.name].orEmpty()
                                if (v.isNotEmpty()) writeText(out, boundary, f.name, v)
                            }
                        }
                    }
                    out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                }
            }

            return readResult(ctx, conn, started)
        } catch (e: IOException) {
            return RunResult(
                false, -1, "Connection error: ${e.javaClass.simpleName}: ${e.message}",
                null, "", "", 0, System.currentTimeMillis() - started
            )
        } finally {
            conn.disconnect()
        }
    }
}
