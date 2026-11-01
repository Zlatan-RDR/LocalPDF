package com.localpdf.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File

enum class EngineState { STOPPED, STARTING, READY, FAILED }

enum class SetupPhase { IDLE, RUNNING, PAUSED, FAILED, DONE }

data class SavedResult(val file: File, val name: String, val bytes: Long, val modified: Long)

// The single source of truth for the screens: what is installed, whether the PDF engine
// (the local Stirling server) is ready, the list of tools, the setup progress and the
// saved results. It starts the engine by itself and stops it after a long idle time.
object AppModel {

    private const val IDLE_STOP_MS = 10L * 60L * 1000L
    private const val KEEP_RESULTS_MS = 7L * 24L * 60L * 60L * 1000L

    // ----- engine -----
    var engine by mutableStateOf(EngineState.STOPPED)
    var engineSeconds by mutableStateOf(0)
    var engineError by mutableStateOf("")

    // ----- tools -----
    var tools by mutableStateOf<List<Tool>>(emptyList())

    // ----- installed components -----
    var coreInstalled by mutableStateOf(false)
    var toolsPackInstalled by mutableStateOf(false)
    var officePackInstalled by mutableStateOf(false)

    // ----- setup / installation -----
    var installing by mutableStateOf(false)
    var showSetup by mutableStateOf(false)
    var setupPhase by mutableStateOf(SetupPhase.IDLE)
    var setupStep by mutableStateOf("")
    var setupStatus by mutableStateOf("")
    var setupFraction by mutableStateOf(-1f)
    var setupError by mutableStateOf("")
    var savedBytes by mutableStateOf(0L)

    // bumped whenever the list of saved results changes
    var resultsVersion by mutableStateOf(0)

    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var visible = false

    private var idleToken = 0
    private var watchdogStarted = false

    @Synchronized
    fun init(ctx: Context) {
        if (appCtx != null) return
        val app = ctx.applicationContext
        appCtx = app
        AutoTranslate.init(app)
        refreshFlags()
        showSetup = !coreInstalled
        tools = Catalog.load(app)
        Thread { pruneResults(app) }.start()
    }

    fun refreshFlags() {
        val a = appCtx ?: return
        coreInstalled = Installer.isCoreInstalled(a)
        toolsPackInstalled = Installer.isPackInstalled(a, Installer.PACK_TOOLS)
        officePackInstalled = Installer.isPackInstalled(a, Installer.PACK_OFFICE)
        savedBytes = Installer.savedDownloadBytes(a)
    }

    // ------------------------------------------------------------------
    // Engine lifecycle
    // ------------------------------------------------------------------

    @Synchronized
    fun ensureEngine(ctx: Context) {
        val app = ctx.applicationContext
        if (!Installer.isCoreInstalled(app)) return
        if (installing) return
        if (engine == EngineState.STARTING) return
        if (engine == EngineState.READY && StirlingServer.isRunning()) return
        engine = EngineState.STARTING
        engineSeconds = 0
        engineError = ""
        Thread {
            try {
                if (!StirlingServer.isRunning()) StirlingServer.start(app)
                val ready = StirlingServer.waitReady(300) { s -> engineSeconds = s }
                if (ready) {
                    loadSpec(app)
                    engine = EngineState.READY
                } else {
                    engineError = StirlingServer.exitInfo() + "\n" + StirlingServer.tailLog(app, 30)
                    engine = EngineState.FAILED
                }
            } catch (e: Throwable) {
                engineError = e.javaClass.simpleName + ": " + e.message
                engine = EngineState.FAILED
            }
        }.start()
    }

    fun restartEngine(ctx: Context) {
        val app = ctx.applicationContext
        Thread {
            try {
                engine = EngineState.STOPPED
                StirlingServer.stop()
            } catch (e: Throwable) {
                // the start below reports any real problem
            }
            ensureEngine(app)
        }.start()
    }

    private fun loadSpec(app: Context) {
        try {
            val r = StirlingServer.httpGet("/v1/api-docs", 30000, 12 * 1024 * 1024)
            if (r.code == 200) {
                Catalog.saveSpec(app, r.body)
                tools = Catalog.load(app)
            }
        } catch (e: Throwable) {
            // the saved list stays in use
        }
    }

    fun onForeground(ctx: Context) {
        val app = ctx.applicationContext
        synchronized(this) {
            visible = true
            idleToken++
        }
        refreshFlags()
        resultsVersion++
        if (coreInstalled && !installing) ensureEngine(app)
        startWatchdog(app)
    }

    // After the app has been out of sight for a while and nothing is running, the engine
    // is stopped to save battery and memory. It starts again when the app is opened.
    fun onBackground(ctx: Context) {
        val token = synchronized(this) {
            visible = false
            ++idleToken
        }
        Thread {
            var waited = 0L
            while (true) {
                try {
                    Thread.sleep(30_000L)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                val stillIdle = synchronized(this@AppModel) { token == idleToken && !visible }
                if (!stillIdle) return@Thread
                waited += 30_000L
                if (waited >= IDLE_STOP_MS && BackgroundWork.snapshot().active == 0 && !installing) {
                    if (StirlingServer.isRunning()) StirlingServer.stop()
                    engine = EngineState.STOPPED
                    return@Thread
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun startWatchdog(app: Context) {
        synchronized(this) {
            if (watchdogStarted) return
            watchdogStarted = true
        }
        Thread {
            while (true) {
                try {
                    Thread.sleep(4000L)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                if (visible && coreInstalled && !installing &&
                    engine == EngineState.READY && !StirlingServer.isRunning()
                ) {
                    engine = EngineState.STOPPED
                    ensureEngine(app)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    // ------------------------------------------------------------------
    // Setup and installation of the components
    // ------------------------------------------------------------------

    // steps: any of "core", "tools", "office", in the order they must be installed
    fun runInstall(ctx: Context, steps: List<String>) {
        val app = ctx.applicationContext
        synchronized(this) {
            if (installing) return
            installing = true
        }
        showSetup = true
        setupPhase = SetupPhase.RUNNING
        setupError = ""
        setupStatus = ""
        setupFraction = -1f
        Thread {
            try {
                if (StirlingServer.isRunning()) StirlingServer.stop()
                engine = EngineState.STOPPED
                for (s in steps) {
                    setupStep = s
                    setupStatus = ""
                    setupFraction = -1f
                    val progress: ProgressCallback = { label, done, total ->
                        setupStatus = Notifier.phaseName(label) + ": " + (done / MB) + " / " + (total / MB) + " MB"
                        setupFraction = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f
                    }
                    val log: (String) -> Unit = { line -> setupStatus = line }
                    if (s == "core") {
                        Installer.installCore(app, progress, log)
                    } else {
                        Installer.installPack(app, s, progress, log)
                    }
                    refreshFlags()
                }
                setupPhase = SetupPhase.DONE
            } catch (e: PausedException) {
                setupPhase = SetupPhase.PAUSED
            } catch (e: Throwable) {
                setupError = describe(e)
                setupPhase = SetupPhase.FAILED
            } finally {
                installing = false
                refreshFlags()
                if (setupPhase == SetupPhase.DONE) ensureEngine(app)
            }
        }.start()
    }

    fun missingSteps(): List<String> {
        val steps = ArrayList<String>()
        if (!coreInstalled) steps.add("core")
        if (!toolsPackInstalled) steps.add("tools")
        if (!officePackInstalled) steps.add("office")
        return steps
    }

    fun pauseInstall() {
        Installer.requestPause()
    }

    private fun describe(e: Throwable): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("ENOSPC") || m.contains("No space", ignoreCase = true) -> Lang.t(
                "المساحة غير كافية على الجهاز. أفرغ بعض المساحة ثم أعد المحاولة.",
                "Not enough free storage. Free some space and try again."
            )
            e is java.net.UnknownHostException || m.contains("Unable to resolve") -> Lang.t(
                "لا يوجد اتصال بالإنترنت.",
                "There is no internet connection."
            )
            else -> m
        }
    }

    // ------------------------------------------------------------------
    // Saved results
    // ------------------------------------------------------------------

    fun resultsDir(ctx: Context): File = File(ctx.filesDir, "results")

    fun listResults(ctx: Context): List<SavedResult> {
        val entries = resultsDir(ctx).listFiles() ?: return emptyList()
        val out = ArrayList<SavedResult>()
        for (e in entries) {
            if (e.isDirectory) {
                val f = e.listFiles()?.firstOrNull { it.isFile } ?: continue
                out.add(SavedResult(f, f.name, f.length(), f.lastModified()))
            } else if (e.isFile) {
                out.add(
                    SavedResult(
                        e,
                        e.name.replaceFirst(Regex("^\\d{10,}_"), ""),
                        e.length(),
                        e.lastModified()
                    )
                )
            }
        }
        return out.sortedByDescending { it.modified }
    }

    fun deleteResult(ctx: Context, r: SavedResult) {
        val parent = r.file.parentFile
        try {
            if (parent != null && parent != resultsDir(ctx)) {
                Installer.deleteTree(parent)
            } else {
                r.file.delete()
            }
        } catch (e: Throwable) {
            // ignore
        }
        resultsVersion++
    }

    fun resultsBytes(ctx: Context): Long {
        val dir = resultsDir(ctx)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearResults(ctx: Context) {
        val entries = resultsDir(ctx).listFiles()
        if (entries != null) {
            for (e in entries) {
                try {
                    Installer.deleteTree(e)
                } catch (x: Throwable) {
                    // ignore
                }
            }
        }
        resultsVersion++
    }

    private fun pruneResults(app: Context) {
        val entries = resultsDir(app).listFiles() ?: return
        val limit = System.currentTimeMillis() - KEEP_RESULTS_MS
        for (e in entries) {
            if (e.lastModified() < limit) {
                try {
                    Installer.deleteTree(e)
                } catch (x: Throwable) {
                    // ignore
                }
            }
        }
        resultsVersion++
    }

    // ------------------------------------------------------------------
    // Problem report
    // ------------------------------------------------------------------

    fun versionName(ctx: Context): String {
        return try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun buildReport(ctx: Context): String {
        val app = ctx.applicationContext
        val sb = StringBuilder()
        sb.append("PDF Lab ").append(versionName(app)).append('\n')
        sb.append("Engine: ").append(engine.name).append("  ").append(StirlingServer.exitInfo()).append('\n')
        sb.append("Core: ").append(coreInstalled)
            .append("  Tools pack: ").append(toolsPackInstalled)
            .append("  Office pack: ").append(officePackInstalled).append("\n\n")
        try {
            sb.append(Diagnostics.deviceInfo(app)).append('\n')
            sb.append(Diagnostics.securityInfo(app)).append('\n')
            sb.append(Diagnostics.rootfsInfo(app)).append("\n\n")
        } catch (e: Throwable) {
            sb.append("(device info unavailable)\n")
        }
        sb.append("--- engine log (last lines) ---\n")
        sb.append(StirlingServer.tailLog(app, 80))
        return sb.toString()
    }
}

// Opening, sharing and saving the result files.
object ResultActions {

    fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun uriFor(ctx: Context, file: File) =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)

    fun share(ctx: Context, file: File) {
        try {
            val send = Intent(Intent.ACTION_SEND)
            send.type = mimeOf(file.name)
            send.putExtra(Intent.EXTRA_STREAM, uriFor(ctx, file))
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, Lang.t("مشاركة", "Share"))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        } catch (e: Throwable) {
            Toast.makeText(ctx, Lang.t("تعذّرت المشاركة", "Sharing failed"), Toast.LENGTH_SHORT).show()
        }
    }

    fun open(ctx: Context, file: File) {
        try {
            val view = Intent(Intent.ACTION_VIEW)
            view.setDataAndType(uriFor(ctx, file), mimeOf(file.name))
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                ctx,
                Lang.t("لا يوجد تطبيق يفتح هذا الملف. استخدم «حفظ» أو «مشاركة».", "No app can open this file. Use Save or Share."),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Throwable) {
            Toast.makeText(ctx, Lang.t("تعذّر فتح الملف", "Could not open the file"), Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText(ctx: Context, subject: String, text: String) {
        try {
            val send = Intent(Intent.ACTION_SEND)
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_SUBJECT, subject)
            send.putExtra(Intent.EXTRA_TEXT, text)
            val chooser = Intent.createChooser(send, subject)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        } catch (e: Throwable) {
            Toast.makeText(ctx, Lang.t("تعذّرت المشاركة", "Sharing failed"), Toast.LENGTH_SHORT).show()
        }
    }
}
