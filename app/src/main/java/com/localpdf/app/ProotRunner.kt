package com.localpdf.app

import android.content.Context
import android.system.Os
import java.io.File
import java.util.concurrent.TimeUnit

data class ProcResult(val exit: Int, val output: String)

object ProotRunner {

    private const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    private const val TEST_SCRIPT = """
echo "== uname =="
uname -a
echo "== qpdf =="
qpdf --version 2>&1 | head -n 1
echo "== ghostscript =="
gs --version 2>&1
echo "== tesseract =="
tesseract --list-langs 2>&1
echo "== files =="
ls -l /opt/stirling 2>&1
echo "== java =="
java -version 2>&1
"""

    private const val JAVA_DIAG_SCRIPT = """
echo "== shim: java -version =="
LD_PRELOAD=/native/libmpshim.so java -version 2>&1
echo "== musl probe inside the rootfs =="
/native/libmprobe_musl.so 2>&1
echo "== limits =="
ulimit -a 2>&1
echo "== jvm files =="
ls -l /usr/lib/jvm/ 2>&1
ls -l /usr/lib/jvm/default-jvm/lib/server 2>&1 | head -n 8
echo "== meminfo =="
head -n 3 /proc/meminfo 2>&1
"""

    private fun nativeDir(ctx: Context): String = ctx.applicationInfo.nativeLibraryDir

    private fun prepareLibLinks(ctx: Context): File {
        val dir = File(ctx.filesDir, "lib")
        dir.mkdirs()
        val link = File(dir, "libtalloc.so.2")
        val target = File(nativeDir(ctx), "libtalloc.so")
        try {
            link.delete()
            Os.symlink(target.absolutePath, link.absolutePath)
        } catch (e: Throwable) {
            // The failure will show up when proot runs.
        }
        return dir
    }

    private fun applyEnv(ctx: Context, env: MutableMap<String, String>, noSeccomp: Boolean) {
        val nd = nativeDir(ctx)
        env["LD_LIBRARY_PATH"] = "${prepareLibLinks(ctx).absolutePath}:$nd"
        env["PROOT_LOADER"] = "$nd/libproot-loader.so"
        env["PROOT_TMP_DIR"] = ctx.cacheDir.absolutePath
        if (noSeccomp) env["PROOT_NO_SECCOMP"] = "1"
    }

    private fun execute(
        ctx: Context,
        cmd: List<String>,
        noSeccomp: Boolean,
        timeoutSec: Long
    ): ProcResult {
        return try {
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            applyEnv(ctx, pb.environment(), noSeccomp)
            val p = pb.start()
            val sb = StringBuilder()
            val reader = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(sb) { sb.append(line).append('\n') }
                    }
                } catch (e: Throwable) {
                    // stream closed
                }
            }
            reader.start()
            val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                synchronized(sb) { sb.append("TIMEOUT after $timeoutSec seconds\n") }
            }
            reader.join(3000)
            val exit = if (finished) p.exitValue() else -1
            ProcResult(exit, synchronized(sb) { sb.toString() })
        } catch (e: Throwable) {
            ProcResult(-2, "ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun prootVersion(ctx: Context): String {
        val r = execute(ctx, listOf("${nativeDir(ctx)}/libproot.so", "--version"), false, 10)
        return "exit=${r.exit}\n${r.output}"
    }

    fun runInGuest(
        ctx: Context,
        script: String,
        noSeccomp: Boolean = false,
        timeoutSec: Long = 90,
        bindNative: Boolean = false
    ): ProcResult {
        val rootfs = Installer.rootfsDir(ctx)
        val cmd = mutableListOf(
            "${nativeDir(ctx)}/libproot.so",
            "-0",
            "--kill-on-exit",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys"
        )
        if (bindNative) {
            cmd.add("-b")
            cmd.add("${nativeDir(ctx)}:/native")
        }
        cmd.addAll(
            listOf(
                "-w", "/",
                "/usr/bin/env", "-i",
                "HOME=/root",
                "PATH=$GUEST_PATH",
                "LANG=C.UTF-8",
                "TERM=xterm",
                "TMPDIR=/tmp",
                "JAVA_HOME=/usr/lib/jvm/default-jvm",
                "/bin/sh", "-c", script
            )
        )
        return execute(ctx, cmd, noSeccomp, timeoutSec)
    }

    fun guestTest(ctx: Context): String {
        if (!Installer.isCoreInstalled(ctx)) return "Core is not installed yet"
        val first = runInGuest(ctx, TEST_SCRIPT)
        if (first.exit == 0) return "exit=0\n${first.output}"
        val second = runInGuest(ctx, TEST_SCRIPT, noSeccomp = true)
        return "attempt 1 (default): exit=${first.exit}\n${first.output}\n" +
            "attempt 2 (PROOT_NO_SECCOMP=1): exit=${second.exit}\n${second.output}"
    }

    fun javaDiagnostics(ctx: Context): String {
        if (!Installer.isCoreInstalled(ctx)) return "Core is not installed yet"
        val r = runInGuest(ctx, JAVA_DIAG_SCRIPT, timeoutSec = 120, bindNative = true)
        return "exit=${r.exit}\n${r.output}"
    }

    fun runProbeDirect(ctx: Context): String {
        val r = execute(ctx, listOf("${nativeDir(ctx)}/libmprobe.so"), false, 30)
        return "exit=${r.exit}\n${r.output}"
    }

    fun runProbeInProot(ctx: Context): String {
        val root = File(ctx.filesDir, "emptyroot")
        for (d in listOf("native", "proc", "dev", "sys", "tmp")) {
            File(root, d).mkdirs()
        }
        val nd = nativeDir(ctx)
        val cmd = listOf(
            "$nd/libproot.so",
            "-0",
            "--kill-on-exit",
            "-r", root.absolutePath,
            "-b", "$nd:/native",
            "-b", "/proc",
            "-b", "/dev",
            "-b", "/sys",
            "-w", "/",
            "/native/libmprobe.so"
        )
        val r = execute(ctx, cmd, false, 30)
        return "exit=${r.exit}\n${r.output}"
    }
}
