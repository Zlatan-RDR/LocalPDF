#define _GNU_SOURCE
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

static void log_line(const char *buf, int n) {
    if (n > 0) {
        syscall(SYS_write, 2, buf, (size_t)n);
    }
}

__attribute__((constructor))
static void shim_loaded(void) {
    char buf[96];
    int n = snprintf(buf, sizeof(buf), "[shim] loaded in pid %d\n", (int)getpid());
    log_line(buf, n);
}

int mprotect(void *addr, size_t len, int prot) {
    long r = syscall(SYS_mprotect, addr, len, prot);
    int e = errno;
    if ((prot & PROT_EXEC) || r != 0) {
        char buf[256];
        int n = snprintf(
            buf, sizeof(buf),
            "[shim] mprotect addr=%p len=%zu prot=%d -> %ld errno=%d (%s)\n",
            addr, len, prot, r, r != 0 ? e : 0, r != 0 ? strerror(e) : "ok"
        );
        log_line(buf, n);
    }
    errno = e;
    return (int)r;
}
