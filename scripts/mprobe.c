#define _GNU_SOURCE
#include <errno.h>
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

static long page_size;

static void report(const char *name, int ok, int err) {
    if (ok) {
        printf("%-36s OK\n", name);
    } else {
        printf("%-36s FAIL (%s)\n", name, strerror(err));
    }
    fflush(stdout);
}

static void put_ret_stub(void *page) {
    uint32_t ret_insn = 0xd65f03c0u;
    memcpy(page, &ret_insn, sizeof(ret_insn));
    __builtin___clear_cache((char *)page, (char *)page + sizeof(ret_insn));
}

static void run_stub(const char *name, void *addr) {
    void (*fn)(void) = (void (*)(void))addr;
    printf("%-36s running...\n", name);
    fflush(stdout);
    fn();
    printf("%-36s OK\n", name);
    fflush(stdout);
}

static void test_anon_rw(void) {
    void *p = mmap(NULL, page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    int e = errno;
    report("1 anon mmap RW", p != MAP_FAILED, e);
    if (p != MAP_FAILED) munmap(p, page_size);
}

static void test_rw_to_wx(void) {
    void *p = mmap(NULL, page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (p == MAP_FAILED) {
        report("2 RW -> mprotect W+X (JVM check)", 0, errno);
        return;
    }
    int r = mprotect(p, page_size, PROT_WRITE | PROT_EXEC);
    int e = errno;
    report("2 RW -> mprotect W+X (JVM check)", r == 0, e);
    munmap(p, page_size);
}

static void test_rw_to_rwx(void) {
    void *p = mmap(NULL, page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (p == MAP_FAILED) {
        report("3 RW -> mprotect R+W+X", 0, errno);
        return;
    }
    int r = mprotect(p, page_size, PROT_READ | PROT_WRITE | PROT_EXEC);
    int e = errno;
    report("3 RW -> mprotect R+W+X", r == 0, e);
    munmap(p, page_size);
}

static void test_direct_rwx(void) {
    void *p = mmap(NULL, page_size, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    int e = errno;
    report("4 anon mmap R+W+X directly", p != MAP_FAILED, e);
    if (p != MAP_FAILED) munmap(p, page_size);
}

static void test_rw_to_rx(void) {
    void *p = mmap(NULL, page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (p == MAP_FAILED) {
        report("5 RW -> mprotect R+X", 0, errno);
        return;
    }
    put_ret_stub(p);
    int r = mprotect(p, page_size, PROT_READ | PROT_EXEC);
    int e = errno;
    report("5 RW -> mprotect R+X", r == 0, e);
    if (r == 0) run_stub("5b execute stub", p);
    munmap(p, page_size);
}

static void test_rw_to_x_only(void) {
    void *p = mmap(NULL, page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (p == MAP_FAILED) {
        report("6 RW -> mprotect X only", 0, errno);
        return;
    }
    put_ret_stub(p);
    int r = mprotect(p, page_size, PROT_EXEC);
    int e = errno;
    report("6 RW -> mprotect X only", r == 0, e);
    munmap(p, page_size);
}

static void test_memfd_double_map(void) {
    int fd = (int)syscall(SYS_memfd_create, "jit", 0);
    if (fd < 0) {
        report("7 memfd_create", 0, errno);
        return;
    }
    if (ftruncate(fd, page_size) != 0) {
        report("7 memfd ftruncate", 0, errno);
        close(fd);
        return;
    }
    void *rw = mmap(NULL, page_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    int e1 = errno;
    report("7a memfd map RW shared", rw != MAP_FAILED, e1);
    void *rx = mmap(NULL, page_size, PROT_READ | PROT_EXEC, MAP_SHARED, fd, 0);
    int e2 = errno;
    report("7b memfd map R+X shared", rx != MAP_FAILED, e2);
    if (rw != MAP_FAILED && rx != MAP_FAILED) {
        put_ret_stub(rw);
        __builtin___clear_cache((char *)rx, (char *)rx + 4);
        run_stub("7c execute via R+X alias", rx);
    }
    if (rw != MAP_FAILED) munmap(rw, page_size);
    if (rx != MAP_FAILED) munmap(rx, page_size);
    close(fd);
}

static void print_selinux(void) {
    FILE *f = fopen("/proc/self/attr/current", "r");
    if (!f) {
        printf("SELinux context: unreadable (%s)\n", strerror(errno));
        return;
    }
    char buf[256];
    memset(buf, 0, sizeof(buf));
    size_t n = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    for (size_t i = 0; i < n; i++) {
        if (buf[i] == '\n' || buf[i] == '\0') {
            buf[i] = '\0';
            break;
        }
    }
    printf("SELinux context: %s\n", buf);
}

static void print_status(void) {
    FILE *f = fopen("/proc/self/status", "r");
    if (!f) return;
    char line[256];
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "Seccomp:", 8) == 0 || strncmp(line, "NoNewPrivs:", 11) == 0) {
            fputs(line, stdout);
        }
    }
    fclose(f);
}

int main(void) {
    page_size = sysconf(_SC_PAGESIZE);
    printf("page size: %ld\n", page_size);
    print_selinux();
    print_status();
    int mdwe = prctl(PR_GET_MDWE, 0, 0, 0, 0);
    if (mdwe < 0) {
        printf("PR_GET_MDWE: unsupported (%s)\n", strerror(errno));
    } else {
        printf("PR_GET_MDWE: %d\n", mdwe);
    }
    fflush(stdout);

    test_anon_rw();
    test_rw_to_wx();
    test_rw_to_rwx();
    test_direct_rwx();
    test_rw_to_rx();
    test_rw_to_x_only();
    test_memfd_double_map();
    printf("done\n");
    return 0;
}
