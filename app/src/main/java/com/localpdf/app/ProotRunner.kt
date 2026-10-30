package com.localpdf.app

import android.content.Context
import android.system.Os
import java.io.File
import java.util.concurrent.TimeUnit

data class ProcResult(val exit: Int, val output: String)

object ProotRunner {

    private const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    // A LibreOffice worker for Stirling, started in the background shortly after the
    // server (documented setup for installs without Docker). Without it Stirling waits
    // about a minute for a worker that never comes before it falls back to soffice.
    private const val UNO_WORKER_START =
        "( sleep 40; exec unoserver --port 2003 --uno-port 2103 ) > /tmp/unoserver.log 2>&1 &\n"

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
LD_PRELOAD=/native/libmpshim.so java -version 2>&1
echo "== proc version =="
ls -l /proc/version 2>&1
head -c 100 /proc/version 2>&1
echo
true
"""

    private const val JAVA_DIAG_SCRIPT = """
echo "== proc version =="
ls -l /proc/version 2>&1
head -c 100 /proc/version 2>&1
echo
echo hello > /tmp/lo_t.txt
rm -f /tmp/uno_out.pdf /tmp/uno_out2.pdf
echo "== unoserver worker test (ports 2013 and 2113) =="
(unoserver --port 2013 --uno-port 2113 > /tmp/unoserver_t.log 2>&1 &)
date +%T
sleep 25
date +%T
echo "== unoconvert run 1 =="
timeout 120 unoconvert --port 2013 --convert-to pdf /tmp/lo_t.txt /tmp/uno_out.pdf > /tmp/uno_c.txt 2>&1
echo "exit code: $?"
date +%T
head -n 5 /tmp/uno_c.txt
ls -l /tmp/uno_out.pdf 2>&1
echo "== unoconvert run 2 =="
timeout 120 unoconvert --port 2013 --convert-to pdf /tmp/lo_t.txt /tmp/uno_out2.pdf > /tmp/uno_c2.txt 2>&1
echo "exit code: $?"
date +%T
head -n 5 /tmp/uno_c2.txt
ls -l /tmp/uno_out2.pdf 2>&1
echo "== unoserver log =="
tail -n 10 /tmp/unoserver_t.log
true
"""

    private fun nativeDir(ctx: Context): String = ctx.applicationInfo.nativeLibraryDir

    // Android hides /proc/version from apps, and LibreOffice's launcher (oosplash)
    // refuses to start without it ("/proc not mounted"). A small stand-in file is
    // bound over /proc/version inside the guest.
    private fun fakeProcVersion(ctx: Context): File {
        val f = File(ctx.filesDir, "fake_proc_version")
        try {
            val text = "Linux version " + (System.getProperty("os.version") ?: "6.6.0") +
                " (localpdf@localhost) (clang) #1 SMP PREEMPT\n"
            if (!f.isFile || f.readText() != text) f.writeText(text)
        } catch (e: Throwable) {
            // The guest test shows whether the bind worked.
        }
        return f
    }

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

    private fun guestCommand(ctx: Context, script: String, bindNative: Boolean): List<String> {
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
        val fake = fakeProcVersion(ctx)
        if (fake.isFile) {
            cmd.add("-b")
            cmd.add("${fake.absolutePath}:/proc/version")
        }
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
                "PROCESS_EXECUTOR_AUTO_UNO_SERVER=false",
                "/bin/sh", "-c", script
            )
        )
        return cmd
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
        return execute(ctx, guestCommand(ctx, script, bindNative), noSeccomp, timeoutSec)
    }

    fun startGuest(ctx: Context, script: String, logFile: File): Process {
        val pb = ProcessBuilder(guestCommand(ctx, UNO_WORKER_START + script, true))
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile))
        applyEnv(ctx, pb.environment(), false)
        return pb.start()
    }

    fun guestTest(ctx: Context): String {
        if (!Installer.isCoreInstalled(ctx)) return "Core is not installed yet"
        val first = runInGuest(ctx, TEST_SCRIPT, timeoutSec = 150, bindNative = true)
        if (first.exit == 0) return "exit=0\n${first.output}"
        val second = runInGuest(ctx, TEST_SCRIPT, noSeccomp = true, timeoutSec = 150, bindNative = true)
        return "attempt 1 (default): exit=${first.exit}\n${first.output}\n" +
            "attempt 2 (PROOT_NO_SECCOMP=1): exit=${second.exit}\n${second.output}"
    }

    fun javaDiagnostics(ctx: Context): String {
        if (!Installer.isCoreInstalled(ctx)) return "Core is not installed yet"
        val r = runInGuest(ctx, JAVA_DIAG_SCRIPT, timeoutSec = 720, bindNative = true)
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
