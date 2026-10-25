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
        Lang.init(applicationContext)
        setContent {
            MaterialTheme {
                val dir = if (Lang.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
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
    "core" -> Lang.t("تنزيل النظام", "Downloading system")
    "jar" -> Lang.t("تنزيل Stirling", "Downloading Stirling")
    "extract" -> Lang.t("فك الضغط", "Extracting")
    else -> label
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val diag = remember { DiagState(ctx.applicationContext) }
    var tab by remember { mutableStateOf(0) }

    val toolsLabel = Lang.t("الأدوات", "Tools")
    val diagLabel = Lang.t("التشخيص", "Diagnostics")

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
                    Button(onClick = { tab = 0 }) { Text(toolsLabel) }
                } else {
                    OutlinedButton(onClick = { tab = 0 }) { Text(toolsLabel) }
                }
                if (tab == 1) {
                    Button(onClick = { tab = 1 }) { Text(diagLabel) }
                } else {
                    OutlinedButton(onClick = { tab = 1 }) { Text(diagLabel) }
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
        d.installed -> Lang.t("إعادة تثبيت النظام الأساسي", "Reinstall the core system")
        d.saved > 0 -> Lang.t(
            "متابعة التنزيل (${d.saved / MB} ميغابايت محفوظة)",
            "Resume download (${d.saved / MB} MB saved)"
        )
        else -> Lang.t(
            "تثبيت النظام الأساسي (نحو 280 ميغابايت)",
            "Install the core system (about 280 MB)"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (d.serverUp) {
                Lang.t("خادم Stirling: يعمل", "Stirling server: running")
            } else {
                Lang.t("خادم Stirling: متوقف", "Stirling server: stopped")
            },
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.installed && !d.serverUp,
            onClick = {
                d.runTask(Lang.t("تشغيل Stirling", "Start Stirling")) {
                    val started = StirlingServer.start(ctx)
                    val t0 = System.currentTimeMillis()
                    val ready = StirlingServer.waitReady(300) { s ->
                        d.status = Lang.t("بدء التشغيل... $s ثانية", "Starting... $s s")
                    }
                    val secs = (System.currentTimeMillis() - t0) / 1000
                    buildString {
                        append(started).append('\n')
                        if (ready) {
                            append(Lang.t("الخادم جاهز بعد $secs ثانية", "Server ready after $secs s")).append('\n')
                        } else {
                            append(
                                Lang.t(
                                    "لم يصبح الخادم جاهزاً (${StirlingServer.exitInfo()}) بعد $secs ثانية",
                                    "The server did not become ready (${StirlingServer.exitInfo()}) after $secs s"
                                )
                            ).append('\n')
                        }
                        append(Lang.t("--- آخر السجل ---", "--- Last log lines ---")).append('\n')
                        append(StirlingServer.tailLog(ctx, 40))
                    }
                }
            }
        ) {
            Text(Lang.t("تشغيل Stirling (محلي)", "Start Stirling (local)"))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.serverUp,
            onClick = {
                d.runTask(Lang.t("فحص الخادم وقائمة API", "Server check and API list")) {
                    StirlingServer.apiSummary(ctx)
                }
            }
        ) {
            Text(Lang.t("فحص الخادم وقائمة API", "Server check and API list"))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy,
            onClick = {
                d.runTask(Lang.t("سجل Stirling", "Stirling log")) {
                    "(${StirlingServer.exitInfo()})\n" + StirlingServer.tailLog(ctx, 80)
                }
            }
        ) {
            Text(Lang.t("عرض سجل Stirling", "Show Stirling log"))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.serverUp,
            onClick = {
                d.runTask(Lang.t("إيقاف Stirling", "Stop Stirling")) {
                    StirlingServer.stop()
                    Lang.t("تم الإيقاف", "Stopped")
                }
            }
        ) {
            Text(Lang.t("إيقاف Stirling", "Stop Stirling"))
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.installed,
            onClick = {
                d.runTask(Lang.t("اختبار البيئة", "Environment test")) { ProotRunner.guestTest(ctx) }
            }
        ) {
            Text(Lang.t("اختبار البيئة (java, qpdf, gs, tesseract)", "Environment test (java, qpdf, gs, tesseract)"))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy && d.installed,
            onClick = {
                d.runTask(Lang.t("تشخيص Java (errno + musl)", "Java diagnostics (errno + musl)")) {
                    ProotRunner.javaDiagnostics(ctx)
                }
            }
        ) {
            Text(Lang.t("تشخيص Java (errno + musl)", "Java diagnostics (errno + musl)"))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !d.busy,
            onClick = {
                d.runTask(Lang.t("تثبيت النظام الأساسي", "Core system install")) {
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
                        log.append(Lang.t("تم الإيقاف المؤقت. التقدم محفوظ.", "Paused. Progress is saved."))
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
                Text(Lang.t("إيقاف مؤقت للتنزيل (يحفظ التقدم)", "Pause download (keeps progress)"))
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
                ctx.startActivity(
                    Intent.createChooser(send, Lang.t("مشاركة التقرير", "Share report"))
                )
            }
        ) {
            Text(Lang.t("مشاركة التقرير", "Share report"))
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
