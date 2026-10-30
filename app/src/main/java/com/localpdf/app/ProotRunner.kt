package com.localpdf.app

import android.content.Context
import android.system.Os
import java.io.File
import java.util.concurrent.TimeUnit

data class ProcResult(val exit: Int, val output: String)

object ProotRunner {

    private const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    // A LibreOffice worker for Stirling, started in the background shortly after the
    // server (documented setup for installs without Docker). Without it the unoconvert
    // client retries for about 40 seconds before Stirling falls back to soffice.
    // The "unoserver" command is not on the PATH in this rootfs, so the virtualenv
    // copy is used; the variants are tried in order until one stays running.
    private const val UNO_WORKER_START =
        "( sleep 40; " +
            "/opt/venv/bin/unoserver --port 2003 --uno-port 2103 || " +
            "/opt/venv/bin/python3 -m unoserver.server --port 2003 --uno-port 2103 || " +
            "/opt/venv/bin/python3 -m unoserver.server --executable soffice --port 2003 --uno-port 2103 " +
            ") > /tmp/unoserver.log 2>&1 &\n"

    // Replaces /usr/local/bin/unoconvert inside the guest. The real client waits about
    // 40 seconds for a worker that is not there before giving up, which made every
    // Office conversion take a minute. This wrapper uses the real client only when the
    // worker is listening and otherwise fails at once, so Stirling goes straight to its
    // direct soffice fallback.
    private val UNOCONVERT_STUB = """#!/bin/sh
if /opt/venv/bin/python3 -c "import socket; s = socket.socket(); s.settimeout(2); s.connect(('127.0.0.1', 2003))" 2>/dev/null; then
  exec /opt/venv/bin/python3 -c "import sys; from unoserver.client import converter_main; sys.exit(converter_main())" "${'$'}@"
fi
echo "LibreOffice worker is not running" >&2
exit 1
"""

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
echo "== uno files =="
ls -l /opt/venv/bin 2>&1 | grep -i uno
ls -l /usr/local/bin 2>&1 | grep -i uno
ls -l /usr/bin/libreoffice /usr/bin/soffice 2>&1
echo "== uno import in venv python =="
/opt/venv/bin/python3 -c "import uno; print('uno import ok')" 2>&1 | tail -n 2
echo hello > /tmp/lo_t.txt
rm -f /tmp/w1.pdf /tmp/w2.pdf /tmp/w3.pdf
echo "== start three worker variants =="
date +%T
(/opt/venv/bin/unoserver --port 2013 --uno-port 2113 > /tmp/w1.log 2>&1 &)
(/opt/venv/bin/python3 -m unoserver.server --port 2014 --uno-port 2114 > /tmp/w2.log 2>&1 &)
(/opt/venv/bin/python3 -m unoserver.server --executable soffice --port 2015 --uno-port 2115 > /tmp/w3.log 2>&1 &)
sleep 45
date +%T
echo "== conversion through worker 1 (port 2013) =="
timeout 100 /opt/venv/bin/python3 -c "import sys; from unoserver.client import converter_main; sys.argv = ['unoconvert', '--port', '2013', '--convert-to', 'pdf', '/tmp/lo_t.txt', '/tmp/w1.pdf']; sys.exit(converter_main())" > /tmp/c1.txt 2>&1
echo "exit code: $?"
date +%T
ls -l /tmp/w1.pdf 2>&1
echo "== worker 1 log =="
tail -n 6 /tmp/w1.log
echo "== conversion through worker 2 (port 2014) =="
timeout 100 /opt/venv/bin/python3 -c "import sys; from unoserver.client import converter_main; sys.argv = ['unoconvert', '--port', '2014', '--convert-to', 'pdf', '/tmp/lo_t.txt', '/tmp/w2.pdf']; sys.exit(converter_main())" > /tmp/c2.txt 2>&1
echo "exit code: $?"
date +%T
ls -l /tmp/w2.pdf 2>&1
echo "== worker 2 log =="
tail -n 6 /tmp/w2.log
echo "== conversion through worker 3 (port 2015) =="
timeout 100 /opt/venv/bin/python3 -c "import sys; from unoserver.client import converter_main; sys.argv = ['unoconvert', '--port', '2015', '--convert-to', 'pdf', '/tmp/lo_t.txt', '/tmp/w3.pdf']; sys.exit(converter_main())" > /tmp/c3.txt 2>&1
echo "exit code: $?"
date +%T
ls -l /tmp/w3.pdf 2>&1
echo "== worker 3 log =="
tail -n 6 /tmp/w3.log
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

    private fun unoconvertStub(ctx: Context): File {
        val f = File(ctx.filesDir, "unoconvert_stub.sh")
        try {
            if (!f.isFile || f.readText() != UNOCONVERT_STUB) f.writeText(UNOCONVERT_STUB)
            f.setExecutable(true, false)
        } catch (e: Throwable) {
            // Without the wrapper the real unoconvert is used (slower, still works).
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
        val stub = unoconvertStub(ctx)
        if (stub.isFile && stub.canExecute()) {
            cmd.add("-b")
            cmd.add("${stub.absolutePath}:/usr/local/bin/unoconvert")
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
