/*
 * smithcore.c -- the ROM's exported ABI + a deterministic bump arena.
 *
 * Freestanding wasm32: no libc, no host imports. We provide our own mem* (clang
 * may emit calls to them for aggregate copies) and a single static arena. The
 * host calls sc_reset() then sc_alloc() to stage inputs/outputs; free is a
 * no-op (sc_reset rewinds everything). Pointers exchanged across the ABI are
 * absolute offsets into the wasm linear memory, which on wasm32 equal the C
 * pointer value of an object in that memory.
 */
#include "core_internal.h"

#define ARENA_SIZE (1 << 20)   /* 1 MiB; clock/struct payloads are tiny */
static u8  g_arena[ARENA_SIZE];
static i32 g_off = 0;

/* --- freestanding mem* --- */
void *memset(void *d, int c, unsigned long n) {
    u8 *p = (u8 *)d;
    for (unsigned long i = 0; i < n; i++) p[i] = (u8)c;
    return d;
}
void *memcpy(void *d, const void *s, unsigned long n) {
    u8 *p = (u8 *)d; const u8 *q = (const u8 *)s;
    for (unsigned long i = 0; i < n; i++) p[i] = q[i];
    return d;
}
void *memmove(void *d, const void *s, unsigned long n) {
    u8 *p = (u8 *)d; const u8 *q = (const u8 *)s;
    if (p < q) for (unsigned long i = 0; i < n; i++) p[i] = q[i];
    else for (unsigned long i = n; i > 0; i--) p[i-1] = q[i-1];
    return d;
}
int memcmp(const void *a, const void *b, unsigned long n) {
    const u8 *x = (const u8 *)a, *y = (const u8 *)b;
    for (unsigned long i = 0; i < n; i++) if (x[i] != y[i]) return x[i] < y[i] ? -1 : 1;
    return 0;
}

static u8 *P(i32 ptr) { return (u8 *)(unsigned long)(unsigned)ptr; }

/* --- arena --- */
__attribute__((export_name("sc_version")))
i32 sc_version(void) { return SC_VERSION; }

__attribute__((export_name("sc_reset")))
void sc_reset(void) { g_off = 0; }

__attribute__((export_name("sc_alloc")))
i32 sc_alloc(i32 len) {
    if (len < 0) return 0;
    i32 aligned = (len + 7) & ~7;
    if (g_off + aligned > ARENA_SIZE || g_off + aligned < g_off) return 0;
    i32 p = (i32)(unsigned long)(g_arena + g_off);
    g_off += aligned;
    return p;
}

/* --- vector clock --- */
__attribute__((export_name("sc_vclock_merge")))
i32 sc_vclock_merge(i32 a, i32 al, i32 b, i32 bl, i32 o, i32 oc) {
    if (al < 0 || bl < 0 || oc < 0) return SC_ERR;
    return vc_merge(P(a), al, P(b), bl, P(o), oc);
}

__attribute__((export_name("sc_vclock_compare")))
i32 sc_vclock_compare(i32 a, i32 al, i32 b, i32 bl) {
    if (al < 0 || bl < 0) return SC_CMP_ERR;
    return vc_compare(P(a), al, P(b), bl);
}

__attribute__((export_name("sc_vclock_canon")))
i32 sc_vclock_canon(i32 in, i32 il, i32 o, i32 oc) {
    if (il < 0 || oc < 0) return SC_ERR;
    return vc_canon(P(in), il, P(o), oc);
}

/* --- sha256 --- */
__attribute__((export_name("sc_sha256")))
i32 sc_sha256(i32 d, i32 l, i32 o) {
    if (l < 0) return SC_ERR;
    sc_sha256_raw(P(d), (u32)l, P(o));
    return 0;
}
