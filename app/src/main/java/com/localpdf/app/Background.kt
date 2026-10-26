package com.localpdf.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

data class WorkSnapshot(
    val active: Int,
    val title: String,
    val text: String,
    val done: Long,
    val total: Long
)

// Builds and posts the notifications: one ongoing notification (shown while the
// background service runs) and one "finished" notification per task.
object Notifier {

    const val CHANNEL_ONGOING = "localpdf_ongoing"
    const val CHANNEL_DONE = "localpdf_done"
    const val ID_ONGOING = 1001

    private var nextDoneId = 2000

    fun phaseName(label: String): String = when (label) {
        "core" -> Lang.t("تنزيل النظام", "Downloading system")
        "jar" -> Lang.t("تنزيل Stirling", "Downloading Stirling")
        "tools" -> Lang.t("تنزيل حزمة الأدوات", "Downloading tools pack")
        "office" -> Lang.t("تنزيل حزمة Office", "Downloading Office pack")
        "extract" -> Lang.t("فك الضغط", "Extracting")
        else -> label
    }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            Lang.t("العمل في الخلفية", "Background work"),
            NotificationManager.IMPORTANCE_LOW
        )
        val done = NotificationChannel(
            CHANNEL_DONE,
            Lang.t("نتائج المهام", "Task results"),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(ongoing)
        nm.createNotificationChannel(done)
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            ctx, 0, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun stopIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, BackgroundService::class.java)
        i.action = BackgroundService.ACTION_STOP
        return PendingIntent.getService(
            ctx, 1, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun buildOngoing(ctx: Context): Notification {
        val s = BackgroundWork.snapshot()
        val busy = s.active > 0
        val title = if (busy) {
            s.title
        } else {
            Lang.t("Stirling يعمل في الخلفية", "Stirling is running in the background")
        }
        val text = if (busy) {
            s.text
        } else {
            Lang.t("جاهز لمعالجة ملفات PDF", "Ready to process PDF files")
        }
        val b = NotificationCompat.Builder(ctx, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, Lang.t("إيقاف", "Stop"), stopIntent(ctx))
        if (busy) {
            if (s.total > 0L) {
                val pct = (s.done * 100L / s.total).toInt().coerceIn(0, 100)
                b.setProgress(100, pct, false)
            } else {
                b.setProgress(0, 0, true)
            }
        }
        return b.build()
    }

    fun updateOngoing(ctx: Context) {
        if (!BackgroundService.running) return
        try {
            ctx.getSystemService(NotificationManager::class.java)
                ?.notify(ID_ONGOING, buildOngoing(ctx))
        } catch (e: Throwable) {
            // a missing notification is not worth failing the task
        }
    }

    fun notifyDone(ctx: Context, ok: Boolean, title: String, text: String) {
        try {
            ensureChannels(ctx)
            val icon = if (ok) {
                android.R.drawable.stat_sys_download_done
            } else {
                android.R.drawable.stat_notify_error
            }
            val n = NotificationCompat.Builder(ctx, CHANNEL_DONE)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openAppIntent(ctx))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            val id = synchronized(this) { nextDoneId++ }
            ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
        } catch (e: Throwable) {
            // notifications are optional
        }
    }
}

// Tracks the running long tasks (installs, tool runs) and the Stirling server,
// keeps the foreground service alive while needed, and holds a partial wake lock
// while a task runs so the work continues with the screen off.
object BackgroundWork {

    // true while the app has a visible screen; set by MainActivity
    @Volatile
    var appVisible: Boolean = true

    private val lock = Any()
    private var active = 0
    private var title = ""
    private var text = ""
    private var done = 0L
    private var total = 0L
    private var lastNotify = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    fun snapshot(): WorkSnapshot = synchronized(lock) {
        WorkSnapshot(active, title, text, done, total)
    }

    private fun acquireWakeLockLocked(app: Context) {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalPDF:work")
            wl.setReferenceCounted(false)
            wl.acquire(3L * 60L * 60L * 1000L)
            wakeLock = wl
        } catch (e: Throwable) {
            // the WAKE_LOCK permission may be missing; the task still runs
        }
    }

    private fun releaseWakeLockLocked() {
        try {
            val wl = wakeLock
            if (wl != null && wl.isHeld) wl.release()
        } catch (e: Throwable) {
            // ignore
        }
        wakeLock = null
    }

    fun begin(ctx: Context, taskTitle: String) {
        val app = ctx.applicationContext
        synchronized(lock) {
            active++
            title = taskTitle
            text = Lang.t("جارٍ التنفيذ...", "Working...")
            done = 0L
            total = 0L
            acquireWakeLockLocked(app)
        }
        BackgroundService.ensureStarted(app)
    }

    fun progress(ctx: Context, label: String, d: Long, t: Long) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val shouldNotify = synchronized(lock) {
            text = Notifier.phaseName(label) + ": " + (d / MB) + " / " + (t / MB) + " MB"
            done = d
            total = t
            if (now - lastNotify >= 1000L || (t > 0L && d >= t)) {
                lastNotify = now
                true
            } else {
                false
            }
        }
        if (shouldNotify) Notifier.updateOngoing(app)
    }

    fun end(ctx: Context, ok: Boolean, doneTitle: String, doneText: String) {
        val app = ctx.applicationContext
        synchronized(lock) {
            active = maxOf(0, active - 1)
            if (active == 0) releaseWakeLockLocked()
        }
        if (!appVisible) Notifier.notifyDone(app, ok, doneTitle, doneText)
        refreshService(app)
    }

    // Forget every running task (used by the notification "Stop" action).
    fun reset() {
        synchronized(lock) {
            active = 0
            releaseWakeLockLocked()
        }
    }

    // Keeps the service alive while a task runs or the server is up, stops it otherwise.
    fun refreshService(ctx: Context) {
        val app = ctx.applicationContext
        val busy = synchronized(lock) { active > 0 }
        if (busy || StirlingServer.isRunning()) {
            BackgroundService.ensureStarted(app)
        } else {
            BackgroundService.stop(app)
        }
    }
}

class BackgroundService : Service() {

    companion object {
        const val ACTION_STOP = "com.localpdf.app.action.STOP"

        @Volatile
        var running: Boolean = false

        fun ensureStarted(ctx: Context) {
            val app = ctx.applicationContext
            if (running) {
                Notifier.updateOngoing(app)
                return
            }
            try {
                Notifier.ensureChannels(app)
                ContextCompat.startForegroundService(app, Intent(app, BackgroundService::class.java))
            } catch (e: Throwable) {
                // the app keeps working without the foreground service
            }
        }

        fun stop(ctx: Context) {
            try {
                val app = ctx.applicationContext
                app.stopService(Intent(app, BackgroundService::class.java))
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Installer.requestPause()
            Thread {
                try {
                    StirlingServer.stop()
                } catch (e: Throwable) {
                    // ignore
                }
                BackgroundWork.reset()
            }.start()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val n = Notifier.buildOngoing(this)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    Notifier.ID_ONGOING,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(Notifier.ID_ONGOING, n)
            }
        } catch (e: Throwable) {
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
