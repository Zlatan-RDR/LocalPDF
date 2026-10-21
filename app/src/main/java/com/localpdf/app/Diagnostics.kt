package com.localpdf.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import java.io.File

const val MB = 1024L * 1024L

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
        }
    }

    fun securityInfo(ctx: Context): String {
        val domain = try {
            File("/proc/self/attr/current").readText().replace("\u0000", "").trim()
        } catch (e: Throwable) {
            "unreadable: ${e.message}"
        }
        val status = try {
            File("/proc/self/status").readLines()
                .filter { it.startsWith("Seccomp:") || it.startsWith("NoNewPrivs:") }
                .joinToString(", ") { it.replace("\t", " ") }
        } catch (e: Throwable) {
            "unreadable: ${e.message}"
        }
        return buildString {
            appendLine("Package: ${ctx.packageName}")
            appendLine("Target : ${ctx.applicationInfo.targetSdkVersion}")
            appendLine("SELinux: $domain")
            appendLine("Status : $status")
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

    fun rootfsInfo(ctx: Context): String {
        val installed = Installer.isCoreInstalled(ctx)
        val jar = File(Installer.rootfsDir(ctx), "opt/stirling/Stirling-PDF.jar")
        val jarText = if (jar.exists()) "${jar.length() / MB} MB" else "missing"
        return "Core installed: $installed, Stirling jar: $jarText"
    }
}
