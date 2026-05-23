/* core_internal.h -- prototypes shared between core translation units. */
#ifndef SMITHCORE_INTERNAL_H
#define SMITHCORE_INTERNAL_H

#include "smithcore.h"

/* sha256.c */
void sc_sha256_raw(const u8 *data, u32 len, u8 out[32]);

/* vclock.c -- operate on the canonical wire form documented in smithcore.h.
 * Return the written length, or SC_ERR. Pointers are absolute offsets resolved
 * by the caller against linear memory. */
i32 vc_merge(const u8 *a, i32 a_len, const u8 *b, i32 b_len, u8 *out, i32 out_cap);
i32 vc_compare(const u8 *a, i32 a_len, const u8 *b, i32 b_len);
i32 vc_canon(const u8 *in, i32 in_len, u8 *out, i32 out_cap);

/* ledger.c -- canonical v2 ledger encoding. in = host-packed fields; out =
 * canonical bytes (header + verbatim prefix + sorted id arrays). out_len/-1. */
i32 ledger_encode(const u8 *in, i32 in_len, u8 *out, i32 out_cap);

#endif
