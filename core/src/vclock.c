/*
 * vclock.c -- vector clock merge / compare / canonicalize over the canonical
 * wire form (see smithcore.h). This is the single implementation that replaces
 * both backend/src/vectorClock.ts and android/.../data/VectorClock.kt.
 *
 * Semantics match the legacy implementations exactly:
 *   merge   -> union of device ids, max count per id
 *   compare -> -1 if a<b, 1 if a>b, 0 if concurrent OR equal (missing == 0)
 * The only thing the core ADDS is a deterministic byte layout: entries sorted
 * ascending by id bytes, zero counts omitted. That canonical order is what
 * removes cross-language drift (JS object insertion order vs Kotlin map order).
 */
#include "core_internal.h"

#define MAX_ENTRIES 1024

typedef struct {
    const u8 *id;
    u16 id_len;
    u32 count;
} Entry;

static u16 rd_u16(const u8 *p) { return (u16)(p[0] | ((u16)p[1] << 8)); }
static u32 rd_u32(const u8 *p) { return (u32)p[0] | ((u32)p[1] << 8) | ((u32)p[2] << 16) | ((u32)p[3] << 24); }
static void wr_u16(u8 *p, u16 v) { p[0] = (u8)v; p[1] = (u8)(v >> 8); }
static void wr_u32(u8 *p, u32 v) { p[0]=(u8)v; p[1]=(u8)(v>>8); p[2]=(u8)(v>>16); p[3]=(u8)(v>>24); }

/* Lexicographic compare of ids: byte-by-byte, shorter is less on a prefix tie. */
static int cmp_id(const u8 *a, u16 alen, const u8 *b, u16 blen) {
    u16 n = alen < blen ? alen : blen;
    for (u16 i = 0; i < n; i++) {
        if (a[i] != b[i]) return a[i] < b[i] ? -1 : 1;
    }
    if (alen == blen) return 0;
    return alen < blen ? -1 : 1;
}

/* Decode wire -> entries. Requires exact consumption. Returns count, or -1. */
static int decode(const u8 *buf, i32 len, Entry *out) {
    if (len < 2) return -1;
    u16 n = rd_u16(buf);
    if (n > MAX_ENTRIES) return -1;
    i32 off = 2;
    for (u16 i = 0; i < n; i++) {
        if (off + 2 > len) return -1;
        u16 idl = rd_u16(buf + off); off += 2;
        if (off + (i32)idl > len) return -1;
        const u8 *id = buf + off; off += idl;
        if (off + 4 > len) return -1;
        u32 c = rd_u32(buf + off); off += 4;
        out[i].id = id; out[i].id_len = idl; out[i].count = c;
    }
    if (off != len) return -1;
    return (int)n;
}

/* Find a device's count in an entry set; missing == 0. */
static u32 find_count(const Entry *es, int n, const u8 *id, u16 idl) {
    for (int i = 0; i < n; i++)
        if (cmp_id(es[i].id, es[i].id_len, id, idl) == 0) return es[i].count;
    return 0;
}

/* Insertion sort entries ascending by id (n is small: device count per cord). */
static void sort_entries(Entry *es, int n) {
    for (int i = 1; i < n; i++) {
        Entry key = es[i];
        int j = i - 1;
        while (j >= 0 && cmp_id(es[j].id, es[j].id_len, key.id, key.id_len) > 0) {
            es[j + 1] = es[j];
            j--;
        }
        es[j + 1] = key;
    }
}

/* Encode entries (assumed already sorted) to canonical wire, omitting zeros. */
static i32 encode(const Entry *es, int n, u8 *out, i32 out_cap) {
    int kept = 0;
    for (int i = 0; i < n; i++) if (es[i].count != 0) kept++;
    if (kept > 0xFFFF) return SC_ERR;
    /* size = 2 + sum(2 + id_len + 4) */
    i32 need = 2;
    for (int i = 0; i < n; i++) if (es[i].count != 0) need += 2 + es[i].id_len + 4;
    if (need > out_cap) return SC_ERR;
    wr_u16(out, (u16)kept);
    i32 off = 2;
    for (int i = 0; i < n; i++) {
        if (es[i].count == 0) continue;
        wr_u16(out + off, es[i].id_len); off += 2;
        for (u16 k = 0; k < es[i].id_len; k++) out[off + k] = es[i].id[k];
        off += es[i].id_len;
        wr_u32(out + off, es[i].count); off += 4;
    }
    return off;
}

i32 vc_canon(const u8 *in, i32 in_len, u8 *out, i32 out_cap) {
    Entry e[MAX_ENTRIES];
    int n = decode(in, in_len, e);
    if (n < 0) return SC_ERR;
    sort_entries(e, n);
    return encode(e, n, out, out_cap);
}

i32 vc_merge(const u8 *a, i32 a_len, const u8 *b, i32 b_len, u8 *out, i32 out_cap) {
    Entry ea[MAX_ENTRIES], eb[MAX_ENTRIES], er[2 * MAX_ENTRIES];
    int na = decode(a, a_len, ea);
    int nb = decode(b, b_len, eb);
    if (na < 0 || nb < 0) return SC_ERR;
    int nr = 0;
    for (int i = 0; i < na; i++) er[nr++] = ea[i];
    for (int i = 0; i < nb; i++) {
        int found = 0;
        for (int j = 0; j < nr; j++) {
            if (cmp_id(er[j].id, er[j].id_len, eb[i].id, eb[i].id_len) == 0) {
                if (eb[i].count > er[j].count) er[j].count = eb[i].count;
                found = 1; break;
            }
        }
        if (!found) er[nr++] = eb[i];
    }
    sort_entries(er, nr);
    return encode(er, nr, out, out_cap);
}

i32 vc_compare(const u8 *a, i32 a_len, const u8 *b, i32 b_len) {
    Entry ea[MAX_ENTRIES], eb[MAX_ENTRIES];
    int na = decode(a, a_len, ea);
    int nb = decode(b, b_len, eb);
    if (na < 0 || nb < 0) return SC_CMP_ERR;
    int a_greater = 0, b_greater = 0;
    for (int i = 0; i < na; i++) {
        u32 ac = ea[i].count;
        u32 bc = find_count(eb, nb, ea[i].id, ea[i].id_len);
        if (ac > bc) a_greater = 1;
        if (bc > ac) b_greater = 1;
    }
    for (int i = 0; i < nb; i++) {
        u32 bc = eb[i].count;
        u32 ac = find_count(ea, na, eb[i].id, eb[i].id_len);
        if (ac > bc) a_greater = 1;
        if (bc > ac) b_greater = 1;
    }
    if (a_greater && !b_greater) return 1;
    if (b_greater && !a_greater) return -1;
    return 0;
}
