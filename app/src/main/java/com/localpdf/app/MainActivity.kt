package com.localpdf.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.concurrent.TimeUnit

private const val MB = 1024L * 1024L

object Diagnostics {

    fun deviceInfo(ctx: Context): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val pageSize = try {
            Os.sysconf(OsConstants._SC_PAGESIZE)
        } catch (e: Throwable) {
            -1L
        }
        val free = try {
            StatFs(ctx.filesDir.absolutePath).availableBytes
        } catch (e: Throwable) {
            -1L
        }
        return buildString {
            appendLine("Device : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABIs   : ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("RAM    : ${mi.totalMem / MB} MB total, ${mi.availMem / MB} MB free")
            appendLine("Page   : $pageSize bytes")
            appendLine("Storage: ${free / MB} MB free in app dir")
            appendLine("LibDir : ${ctx.applicationInfo.nativeLibraryDir}")
        }
    }

    fun nativeLibsInfo(ctx: Context): String {
        val dir = File(ctx.applicationInfo.nativeLibraryDir)
        val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
        if (files.isEmpty()) return "No native libs found in ${dir.absolutePath}"
        return files.joinToString("\n") { f ->
            "${f.name}  ${f.length() / 1024} KB  exec=${f.canExecute()}"
        }
    }

    private fun prepareLibLinks(ctx: Context): File {
        val dir = File(ctx.filesDir, "lib")
        dir.mkdirs()
        val link = File(dir, "libtalloc.so.2")
        val target = File(ctx.applicationInfo.nativeLibraryDir, "libtalloc.so")
        try {
            link.delete()
            Os.symlink(target.absolutePath, link.absolutePath)
        } catch (e: Throwable) {
            // The failure will show up when proot runs.
        }
        return dir
    }

    fun runProotVersion(ctx: Context): String {
        return try {
            val nativeDir = ctx.applicationInfo.nativeLibraryDir
            val libDir = prepareLibLinks(ctx)
            val pb = ProcessBuilder("$nativeDir/libproot.so", "--version")
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env["LD_LIBRARY_PATH"] = "${libDir.absolutePath}:$nativeDir"
            env["PROOT_LOADER"] = "$nativeDir/libproot-loader.so"
            env["PROOT_TMP_DIR"] = ctx.cacheDir.absolutePath
            val p = pb.start()
            val finished = p.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                "TIMEOUT after 10 seconds"
            } else {
                val out = p.inputStream.bufferedReader().readText()
                "exit=${p.exitValue()}\n$out"
            }
        } catch (e: Throwable) {
            "ERROR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DiagnosticsScreen()
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen() {
    val ctx = LocalContext.current
    val base = remember {
        Diagnostics.deviceInfo(ctx) + "\nNative libs:\n" + Diagnostics.nativeLibsInfo(ctx)
    }
    var report by remember { mutableStateOf(base) }
    var busy by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "LocalPDF - تشخيص المرحلة 0",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        Thread {
                            val result = Diagnostics.runProotVersion(ctx)
                            report = base + "\n\n--- proot --version ---\n" + result
                            busy = false
                        }.start()
                    }
                ) {
                    Text("اختبار proot")
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        ctx.startActivity(Intent.createChooser(send, "مشاركة التقرير"))
                    }
                ) {
                    Text("مشاركة التقرير")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = report,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
