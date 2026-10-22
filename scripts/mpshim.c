#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef PR_GET_MDWE
#define PR_GET_MDWE 66
#endif

static char filebuf[1 << 19];
static int diag_done = 0;
static int mmap_logs = 0;

static void outf(const char *fmt, ...) {
    char buf[600];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    if (n > 0) {
        if (n >= (int)sizeof(buf)) n = (int)sizeof(buf) - 1;
        syscall(SYS_write, 2, buf, (size_t)n);
    }
}

static size_t load_file(const char *path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        outf("[shim] cannot open %s: %s\n", path, strerror(errno));
        return 0;
    }
    size_t total = 0;
    ssize_t r;
    while (total < sizeof(filebuf) - 1 &&
           (r = read(fd, filebuf + total, sizeof(filebuf) - 1 - total)) > 0) {
        total += (size_t)r;
    }
    close(fd);
    filebuf[total] = '\0';
    return total;
}

static void print_matching_lines(const char *path, const char *p1, const char *p2) {
    if (load_file(path) == 0) return;
    char *line = filebuf;
    while (*line) {
        char *nl = strchr(line, '\n');
        if (nl) *nl = '\0';
        int match = 0;
        if (p1 && strncmp(line, p1, strlen(p1)) == 0) match = 1;
        if (p2 && strncmp(line, p2, strlen(p2)) == 0) match = 1;
        if (match) outf("[shim] %s\n", line);
        if (!nl) break;
        line = nl + 1;
    }
}

static void print_maps_for(uintptr_t addr) {
    if (load_file("/proc/self/maps") == 0) return;
    char *line = filebuf;
    while (*line) {
        char *nl = strchr(line, '\n');
        if (nl) *nl = '\0';
        unsigned long s = 0, e = 0;
        if (sscanf(line, "%lx-%lx", &s, &e) == 2 && addr >= s && addr < e) {
            outf("[shim] maps: %s\n", line);
        }
        if (!nl) break;
        line = nl + 1;
    }
}

static void diagnose(void *addr, size_t len) {
    outf("[shim] diag: tid=%ld pid=%d\n", (long)syscall(SYS_gettid), (int)getpid());
    outf("[shim] PR_GET_MDWE=%ld\n", (long)syscall(SYS_prctl, PR_GET_MDWE, 0L, 0L, 0L, 0L));
    print_matching_lines("/proc/self/status", "Seccomp:", "NoNewPrivs:");
    print_matching_lines("/proc/self/attr/current", "", NULL);
    print_maps_for((uintptr_t)addr);

    static const int combos[] = {
        PROT_READ | PROT_EXEC,
        PROT_WRITE | PROT_EXEC,
        PROT_EXEC,
        PROT_READ | PROT_WRITE | PROT_EXEC
    };
    for (size_t i = 0; i < sizeof(combos) / sizeof(combos[0]); i++) {
        long rr = syscall(SYS_mprotect, addr, len, combos[i]);
        int ee = errno;
        outf("[shim] retry same page prot=%d -> %ld errno=%d\n", combos[i], rr, rr != 0 ? ee : 0);
        syscall(SYS_mprotect, addr, len, PROT_READ | PROT_WRITE);
    }

    long fresh = syscall(SYS_mmap, NULL, (size_t)4096, PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0L);
    if (fresh == -1) {
        outf("[shim] fresh mmap failed errno=%d\n", errno);
        return;
    }
    long fr = syscall(SYS_mprotect, (void *)fresh, (size_t)4096,
                      PROT_READ | PROT_WRITE | PROT_EXEC);
    int fe = errno;
    outf("[shim] fresh page at %p RW->RWX -> %ld errno=%d\n",
         (void *)fresh, fr, fr != 0 ? fe : 0);
    syscall(SYS_munmap, (void *)fresh, (size_t)4096);
}

__attribute__((constructor))
static void shim_loaded(void) {
    outf("[shim] loaded in pid %d, PR_GET_MDWE=%ld\n", (int)getpid(),
         (long)syscall(SYS_prctl, PR_GET_MDWE, 0L, 0L, 0L, 0L));
}

void *mmap(void *addr, size_t len, int prot, int flags, int fd, off_t off) {
    long r = syscall(SYS_mmap, addr, len, prot, flags, fd, (long)off);
    int e = errno;
    if (len == 4096 && prot == (PROT_READ | PROT_WRITE) && mmap_logs < 20) {
        mmap_logs++;
        outf("[shim] mmap len=%zu prot=%d flags=0x%x fd=%d -> %p\n",
             len, prot, flags, fd, (void *)r);
    }
    errno = e;
    return r == -1 ? MAP_FAILED : (void *)r;
}

int mprotect(void *addr, size_t len, int prot) {
    long r = syscall(SYS_mprotect, addr, len, prot);
    int e = errno;
    if ((prot & PROT_EXEC) || r != 0) {
        outf("[shim] mprotect addr=%p len=%zu prot=%d -> %ld errno=%d (%s)\n",
             addr, len, prot, r, r != 0 ? e : 0, r != 0 ? strerror(e) : "ok");
        if (r != 0 && (prot & PROT_EXEC) && !diag_done) {
            diag_done = 1;
            diagnose(addr, len);
        }
    }
    errno = e;
    return (int)r;
}
