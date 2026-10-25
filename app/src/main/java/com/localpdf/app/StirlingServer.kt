package com.localpdf.app

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class HttpResult(val code: Int, val body: String)

object StirlingServer {

    const val PORT = 8080
    private const val BASE = "http://127.0.0.1:$PORT"

    @Volatile
    private var process: Process? = null

    // If /opt/stirling/natives contains at least one .jar (for example the
    // jpdfium-natives-linux-arm64 jar), the server is started through Spring Boot's
    // PropertiesLauncher with loader.path so those jars join the classpath.
    // Otherwise the original "java -jar" command is used.
    private val START_SCRIPT = """
cd /data
export DISABLE_ADDITIONAL_FEATURES=true
export LD_PRELOAD=/native/libmpshim.so
if ls /opt/stirling/natives/*.jar >/dev/null 2>&1; then
  exec java -Xmx1536m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true -Djava.io.tmpdir=/tmp -Dloader.path=/opt/stirling/natives -cp /opt/stirling/Stirling-PDF.jar org.springframework.boot.loader.launch.PropertiesLauncher --server.port=$PORT --server.address=127.0.0.1
fi
exec java -Xmx1536m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true -Djava.io.tmpdir=/tmp -jar /opt/stirling/Stirling-PDF.jar --server.port=$PORT --server.address=127.0.0.1
"""

    fun logFile(ctx: Context): File = File(ctx.filesDir, "stirling.log")

    fun isRunning(): Boolean = process?.isAlive == true

    @Synchronized
    fun start(ctx: Context): String {
        if (isRunning()) return "Server process is already running"
        if (!Installer.isCoreInstalled(ctx)) return "Core is not installed"
        val log = logFile(ctx)
        log.delete()
        process = ProotRunner.startGuest(ctx, START_SCRIPT, log)
        return "Server process started"
    }

    @Synchronized
    fun stop() {
        val p = process ?: return
        p.destroy()
        try {
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (e: Throwable) {
            p.destroyForcibly()
        }
        process = null
    }

    fun exitInfo(): String {
        val p = process ?: return "no process"
        return if (p.isAlive) "running" else "exited with code ${p.exitValue()}"
    }

    fun tailLog(ctx: Context, lines: Int): String {
        val f = logFile(ctx)
        if (!f.exists()) return "(no log yet)"
        val maxBytes = 24 * 1024L
        RandomAccessFile(f, "r").use { raf ->
            val start = maxOf(0L, raf.length() - maxBytes)
            raf.seek(start)
            val bytes = ByteArray((raf.length() - start).toInt())
            raf.readFully(bytes)
            val text = String(bytes, Charsets.UTF_8)
            return text.lines().takeLast(lines).joinToString("\n")
        }
    }

    fun httpGet(path: String, timeoutMs: Int, maxBytes: Int): HttpResult {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        try {
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val out = ByteArrayOutputStream()
            if (stream != null) {
                stream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (out.size() < maxBytes) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            return HttpResult(code, out.toString("UTF-8"))
        } finally {
            conn.disconnect()
        }
    }

    fun waitReady(timeoutSec: Int, onTick: (Int) -> Unit): Boolean {
        val start = System.currentTimeMillis()
        while ((System.currentTimeMillis() - start) / 1000 < timeoutSec) {
            val p = process
            if (p == null || !p.isAlive) return false
            try {
                val r = httpGet("/", 2000, 2000)
                if (r.code in 200..499) return true
            } catch (e: Throwable) {
                // not ready yet
            }
            onTick(((System.currentTimeMillis() - start) / 1000).toInt())
            Thread.sleep(2000)
        }
        return false
    }

    // Lists every endpoint of the server (not only the first 60).
    fun apiSummary(ctx: Context): String {
        val sb = StringBuilder()
        for (path in listOf("/v1/api-docs", "/v3/api-docs")) {
            try {
                val r = httpGet(path, 20000, 12 * 1024 * 1024)
                if (r.code != 200) {
                    sb.append("$path -> HTTP ${r.code}\n")
                    continue
                }
                File(ctx.filesDir, "openapi.json").writeText(r.body)
                val paths = JSONObject(r.body).getJSONObject("paths")
                val keys = mutableListOf<String>()
                val it = paths.keys()
                while (it.hasNext()) keys.add(it.next())
                keys.sort()
                sb.append("$path -> ${keys.size} endpoints, ${r.body.length / 1024} KB saved as openapi.json\n")
                for (k in keys) sb.append(k).append('\n')
                return sb.toString()
            } catch (e: Throwable) {
                sb.append("$path -> ${e.javaClass.simpleName}: ${e.message}\n")
            }
        }
        return sb.toString()
    }
}
