package com.localpdf.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
                    AppRoot()
                }
            }
        }
    }
}

class DiagState(private val appCtx: Context) {
    var report by mutableStateOf("")
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var fraction by mutableStateOf(-1f)
    var installed by mutableStateOf(false)
    var saved by mutableStateOf(0L)
    var serverUp by mutableStateOf(false)

    init {
        report = Diagnostics.deviceInfo(appCtx) + "\n" +
            Diagnostics.securityInfo(appCtx) + "\nNative libs:\n" +
            Diagnostics.nativeLibsInfo(appCtx) + "\n" +
            Diagnostics.rootfsInfo(appCtx)
        installed = Installer.isCoreInstalled(appCtx)
        saved = Installer.savedDownloadBytes(appCtx)
        serverUp = StirlingServer.isRunning()
    }

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
            saved = Installer.savedDownloadBytes(appCtx)
            serverUp = StirlingServer.isRunning()
            busy = false
        }.start()
    }
}

private fun phaseName(label: String): String = when (label) {
    "core" -> "تنزيل النظام"
    "jar" -> "تنزيل Stirling"
    "extract" -> "فك الضغط"
    else -> label
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val diag = remember { DiagState(ctx.applicationContext) }
    var tab by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tab == 0) {
                    Button(onClick = { tab = 0 }) { Text("الأدوات") }
                } else {
                    OutlinedButton(onClick = { tab = 0 }) { Text("الأدوات") }
                }
                if (tab == 1) {
                    Button(onClick = { tab = 1 }) { Text("التشخيص") }
                } else {
                    OutlinedButton(onClick = { tab = 1 }) { Text("التشخيص") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (tab == 0) {
                    ToolsScreen()
                } else {
                    DiagnosticsScreen(diag)
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(d: DiagState) {
    val ctx = LocalContext.current
    val view = LocalView.current

    SideEffect { view.keepScreenOn = d.busy || d.serverUp }

    val installLabel = when {
        d.installed -> "إعادة تثبيت النظام الأساسي"
        d.saved > 0 -> "متابعة التنزيل (${d.saved / MB} ميغابايت محفوظة)"
        else -> "تثبيت النظام الأساسي (نحو 260 ميغابايت)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (d.serverUp) "خادم Stirling: يعمل" else "خادم Stirling: متوقف",
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.installed && !d.serverUp,
            onClick = {
                d.runTask("تشغيل Stirling") {
                    val started = StirlingServer.start(ctx)
                    val t0 = System.currentTimeMillis()
                    val ready = StirlingServer.waitReady(300) { s ->
                        d.status = "بدء التشغيل... $s ثانية"
                    }
                    val secs = (System.currentTimeMillis() - t0) / 1000
                    buildString {
                        append(started).append('\n')
                        if (ready) {
                            append("الخادم جاهز بعد $secs ثانية\n")
                        } else {
                            append("لم يصبح الخادم جاهزاً (${StirlingServer.exitInfo()}) بعد $secs ثانية\n")
                        }
                        append("--- آخر السجل ---\n")
                        append(StirlingServer.tailLog(ctx, 40))
                    }
                }
            }
        ) {
            Text("تشغيل Stirling (محلي)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.serverUp,
            onClick = {
                d.runTask("فحص الخادم وقائمة API") { StirlingServer.apiSummary(ctx) }
            }
        ) {
            Text("فحص الخادم وقائمة API")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy,
            onClick = {
                d.runTask("سجل Stirling") {
                    "(${StirlingServer.exitInfo()})\n" + StirlingServer.tailLog(ctx, 80)
                }
            }
        ) {
            Text("عرض سجل Stirling")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.serverUp,
            onClick = {
                d.runTask("إيقاف Stirling") {
                    StirlingServer.stop()
                    "تم الإيقاف"
                }
            }
        ) {
            Text("إيقاف Stirling")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.installed,
            onClick = {
                d.runTask("اختبار البيئة") { ProotRunner.guestTest(ctx) }
            }
        ) {
            Text("اختبار البيئة (java, qpdf, gs, tesseract)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.installed,
            onClick = {
                d.runTask("تشخيص Java (errno + musl)") { ProotRunner.javaDiagnostics(ctx) }
            }
        ) {
            Text("تشخيص Java (errno + musl)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy,
            onClick = {
                d.runTask("تثبيت النظام الأساسي") {
                    val log = StringBuilder()
                    try {
                        Installer.installCore(
                            ctx,
                            onProgress = { label, done, total ->
                                d.status = "${phaseName(label)}: ${done / MB} / ${total / MB} MB"
                                d.fraction = if (total > 0) done.toFloat() / total else -1f
                            },
                            log = { line -> synchronized(log) { log.append(line).append('\n') } }
                        )
                        d.installed = Installer.isCoreInstalled(ctx)
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

        if (d.busy) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { Installer.requestPause() }
            ) {
                Text("إيقاف مؤقت للتنزيل (يحفظ التقدم)")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, d.report)
                }
                ctx.startActivity(Intent.createChooser(send, "مشاركة التقرير"))
            }
        ) {
            Text("مشاركة التقرير")
        }

        if (d.busy) {
            Spacer(modifier = Modifier.height(12.dp))
            if (d.fraction >= 0f) {
                LinearProgressIndicator(
                    progress = { d.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (d.status.isNotEmpty()) {
                Text(text = d.status, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = d.report,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
