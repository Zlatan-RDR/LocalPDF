package com.localpdf.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainScreen()
                }
            }
        }
    }
}

private fun phaseName(label: String): String = when (label) {
    "core" -> "تنزيل النظام"
    "jar" -> "تنزيل Stirling"
    "extract" -> "فك الضغط"
    else -> label
}

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val base = remember {
        Diagnostics.deviceInfo(ctx) + "\n" +
            Diagnostics.securityInfo(ctx) + "\nNative libs:\n" +
            Diagnostics.nativeLibsInfo(ctx)
    }
    var report by remember { mutableStateOf(base + "\n" + Diagnostics.rootfsInfo(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var fraction by remember { mutableStateOf(-1f) }
    var installed by remember { mutableStateOf(Installer.isCoreInstalled(ctx)) }
    var saved by remember { mutableStateOf(Installer.savedDownloadBytes(ctx)) }

    SideEffect { view.keepScreenOn = busy }

    fun runTask(title: String, block: () -> String) {
        busy = true
        Thread {
            val result = try {
                block()
            } catch (e: Throwable) {
                "ERROR: ${e.javaClass.simpleName}: ${e.message}"
            }
            report = report + "\n\n--- " + title + " ---\n" + result
            status = ""
            fraction = -1f
            saved = Installer.savedDownloadBytes(ctx)
            busy = false
        }.start()
    }

    val installLabel = when {
        installed -> "إعادة تثبيت النظام الأساسي"
        saved > 0 -> "متابعة التنزيل (${saved / MB} ميغابايت محفوظة)"
        else -> "تثبيت النظام الأساسي (نحو 260 ميغابايت)"
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "LocalPDF - المرحلة 1",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && installed,
                onClick = {
                    runTask("تشخيص Java (errno + musl)") { ProotRunner.javaDiagnostics(ctx) }
                }
            ) {
                Text("تشخيص Java (errno + musl)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    runTask("اختبار الذاكرة التنفيذية (mprobe)") {
                        "== direct (app process) ==\n" + ProotRunner.runProbeDirect(ctx) +
                            "\n== inside proot ==\n" + ProotRunner.runProbeInProot(ctx)
                    }
                }
            ) {
                Text("اختبار الذاكرة التنفيذية (mprobe)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    runTask("تثبيت النظام الأساسي") {
                        val log = StringBuilder()
                        try {
                            Installer.installCore(
                                ctx,
                                onProgress = { label, done, total ->
                                    status = "${phaseName(label)}: ${done / MB} / ${total / MB} MB"
                                    fraction = if (total > 0) done.toFloat() / total else -1f
                                },
                                log = { line -> synchronized(log) { log.append(line).append('\n') } }
                            )
                            installed = Installer.isCoreInstalled(ctx)
                            log.append("OK")
                        } catch (e: PausedException) {
                            log.append("تم الإيقاف المؤقت. التقدم محفوظ.")
                        } catch (e: Throwable) {
                            log.append("ERROR: ${e.javaClass.simpleName}: ${e.message}")
                        }
                        log.toString()
                    }
                }
            ) {
                Text(installLabel)
            }

            if (busy) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { Installer.requestPause() }
                ) {
                    Text("إيقاف مؤقت (يحفظ التقدم)")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && installed,
                onClick = {
                    runTask("اختبار البيئة") { ProotRunner.guestTest(ctx) }
                }
            ) {
                Text("اختبار البيئة (java, qpdf, gs, tesseract)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    runTask("proot --version") { ProotRunner.prootVersion(ctx) }
                }
            ) {
                Text("اختبار proot")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
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

            if (busy) {
                Spacer(modifier = Modifier.height(12.dp))
                if (fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (status.isNotEmpty()) {
                    Text(text = status, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
