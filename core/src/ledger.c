/*
 * ledger.c -- canonical v2 ledger encoding (the "packed struct"). Reads the
 * host-packed input buffer (see smithcore.h) and emits the canonical v2 bytes:
 * the "SMC" header, fields 1-9 copied verbatim, then the three id arrays sorted
 * ascending by unsigned utf-8 bytes. The sort is the cross-language drift point
 * the core centralizes; the host keeps only the float->integer rounding (the
 * core is float-free). Byte layout is identical to the M2 host encoder, proven
 * by the committed golden vectors.
 */
#include "core_internal.h"

#define MAX_IDS 1024   /* per id-array element cap (ids per artifact are tiny) */

typedef struct { const u8 *ptr; u32 len; } Str;

static u32 rd_u32(const u8 *p) {
    return (u32)p[0] | ((u32)p[1] << 8) | ((u32)p[2] << 16) | ((u32)p[3] << 24);
}
static void wr_u32(u8 *p, u32 v) { p[0]=(u8)v; p[1]=(u8)(v>>8); p[2]=(u8)(v>>16); p[3]=(u8)(v>>24); }

/* Unsigned byte compare; shorter is less on a prefix tie (matches Buffer.compare). */
static int cmp_bytes(const u8 *a, u32 alen, const u8 *b, u32 blen) {
    u32 n = alen < blen ? alen : blen;
    for (u32 i = 0; i < n; i++) if (a[i] != b[i]) return a[i] < b[i] ? -1 : 1;
    if (alen == blen) return 0;
    return alen < blen ? -1 : 1;
}

/* Skip one string [u32 len][bytes]; advance *off. Returns 0 ok, -1 overflow. */
static int skip_str(const u8 *buf, i32 len, i32 *off) {
    if (*off + 4 > len) return -1;
    u32 l = rd_u32(buf + *off); *off += 4;
    if (l > (u32)(len - *off)) return -1;   /* *off <= len here; no signed overflow */
    *off += (i32)l;
    return 0;
}

/* Skip one strarray [u32 count][count strings]; advance *off. */
static int skip_strarray(const u8 *buf, i32 len, i32 *off) {
    if (*off + 4 > len) return -1;
    u32 c = rd_u32(buf + *off); *off += 4;
    for (u32 i = 0; i < c; i++) if (skip_str(buf, len, off) < 0) return -1;
    return 0;
}

/* Read a strarray into Str[] (ptr/len pairs). Returns count, or -1. */
static int read_strarray(const u8 *buf, i32 len, i32 *off, Str *out, int cap) {
    if (*off + 4 > len) return -1;
    u32 c = rd_u32(buf + *off); *off += 4;
    if (c > (u32)cap) return -1;
    for (u32 i = 0; i < c; i++) {
        if (*off + 4 > len) return -1;
        u32 l = rd_u32(buf + *off); *off += 4;
        if (l > (u32)(len - *off)) return -1;
        out[i].ptr = buf + *off; out[i].len = l; *off += (i32)l;
    }
    return (int)c;
}

/* Insertion sort Str[] ascending by unsigned utf-8 bytes (n is small). */
static void sort_strs(Str *s, int n) {
    for (int i = 1; i < n; i++) {
        Str key = s[i];
        int j = i - 1;
        while (j >= 0 && cmp_bytes(s[j].ptr, s[j].len, key.ptr, key.len) > 0) {
            s[j + 1] = s[j]; j--;
        }
        s[j + 1] = key;
    }
}

/* Emit a strarray: [u32 count] + each [u32 len][bytes]. Advance *off. 0/-1. */
static int emit_strarray(u8 *out, i32 out_cap, i32 *off, const Str *s, int n) {
    if (*off + 4 > out_cap) return -1;
    wr_u32(out + *off, (u32)n); *off += 4;
    for (int i = 0; i < n; i++) {
        if ((i64)*off + 4 + (i64)s[i].len > (i64)out_cap) return -1;
        wr_u32(out + *off, s[i].len); *off += 4;
        for (u32 k = 0; k < s[i].len; k++) out[*off + k] = s[i].ptr[k];
        *off += (i32)s[i].len;
    }
    return 0;
}

i32 ledger_encode(const u8 *in, i32 in_len, u8 *out, i32 out_cap) {
    if (in_len < 0 || out_cap < 0) return SC_ERR;

    /* Walk fields 1-9 (3 strings + 4 strarrays + 2 i64) to find prefix_end. */
    i32 off = 0;
    for (int i = 0; i < 3; i++) if (skip_str(in, in_len, &off) < 0) return SC_ERR;
    for (int i = 0; i < 4; i++) if (skip_strarray(in, in_len, &off) < 0) return SC_ERR;
    if (off + 16 > in_len) return SC_ERR;   /* totalCostCents + totalHoursCenti */
    off += 16;
    i32 prefix_end = off;                    /* fields 1-9 = [0, prefix_end) */

    /* Header + verbatim prefix copy. */
    if ((i64)5 + (i64)prefix_end > (i64)out_cap) return SC_ERR;
    i32 woff = 0;
    out[woff++] = 0x53; out[woff++] = 0x4d; out[woff++] = 0x43; /* "SMC" */
    out[woff++] = 0x01;                       /* encoding abi tag (matches M2 golden) */
    out[woff++] = 0x02;                       /* format v2 */
    for (i32 i = 0; i < prefix_end; i++) out[woff + i] = in[i];
    woff += prefix_end;

    /* Three id arrays: read, sort, emit. */
    Str ids[MAX_IDS];
    for (int k = 0; k < 3; k++) {
        int n = read_strarray(in, in_len, &off, ids, MAX_IDS);
        if (n < 0) return SC_ERR;
        sort_strs(ids, n);
        if (emit_strarray(out, out_cap, &woff, ids, n) < 0) return SC_ERR;
    }
    if (off != in_len) return SC_ERR;         /* exact consumption */
    return woff;
}
