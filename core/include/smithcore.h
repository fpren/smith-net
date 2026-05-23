/*
 * smithcore.h -- the SmithNet "ROM" public ABI.
 *
 * This is the single deterministic core compiled to ONE smithcore.wasm that
 * every host (Node backend, Android, iOS, Pi, browser) loads UNCHANGED. The
 * host provides display / IO / time / keys; it never edits the ROM.
 *
 * Hard invariants (see docs and the project determinism NFRs D1..D5):
 *   - No floating point anywhere in the core (NaN / rounding diverge per host).
 *   - Explicit little-endian wire encoding for all multi-byte integers.
 *   - Single-threaded, pure function of its inputs. No time, no RNG, no UUID,
 *     no key material, no syscalls, no host imports.
 *
 * Memory model: the host calls sc_reset(), then sc_alloc(len) to obtain
 * pointers into the wasm linear memory, writes input bytes there, calls an
 * export, and reads output bytes back. free is a no-op; sc_reset() rewinds the
 * whole arena between operations.
 *
 * Vector-clock canonical wire format (the M1 pilot surface):
 *   u16  n                      ; entry count, LE
 *   repeat n times, entries sorted ascending by id bytes:
 *     u16 id_len                ; LE
 *     u8  id[id_len]            ; UTF-8 device id
 *     u32 count                 ; LE, always >= 1 (zero entries are omitted)
 * This canonical form is what makes {a:0} and {} hash identically and what two
 * different host languages can agree on byte-for-byte.
 */
#ifndef SMITHCORE_H
#define SMITHCORE_H

typedef unsigned char  u8;
typedef unsigned short u16;
typedef unsigned int   u32;
typedef unsigned long long u64;
typedef long long      i64;
typedef int            i32;

#define SC_VERSION 3

/* Sentinel returned by size-producing exports when out_cap is too small or a
 * buffer fails to parse. Callers must treat any negative return as an error. */
#define SC_ERR (-1)

/* compare returns -1/0/1, so it needs a sentinel OUTSIDE that set. 2 = error. */
#define SC_CMP_ERR (2)

/* --- arena --- */
i32 sc_version(void);
void sc_reset(void);
i32  sc_alloc(i32 len);   /* returns ptr into linear memory, or 0 on OOM */

/* --- vector clock (M1 pilot) --- */
/* Merge a and b (union, max per device id). Writes canonical wire form to out.
 * Returns out_len, or SC_ERR. */
i32 sc_vclock_merge(i32 a_ptr, i32 a_len, i32 b_ptr, i32 b_len,
                    i32 out_ptr, i32 out_cap);
/* Causal compare. Returns -1 (a<b), 0 (concurrent/equal), 1 (a>b), or SC_CMP_ERR. */
i32 sc_vclock_compare(i32 a_ptr, i32 a_len, i32 b_ptr, i32 b_len);
/* Canonicalize an arbitrary (possibly unsorted, possibly zero-bearing) clock
 * into the canonical wire form. Returns out_len, or SC_ERR. */
i32 sc_vclock_canon(i32 in_ptr, i32 in_len, i32 out_ptr, i32 out_cap);

/* --- sha256 (scaffold for M2 ledger/audit; also a strong parity probe) --- */
/* Writes 32 raw bytes to out32_ptr. Returns 0, or SC_ERR. */
i32 sc_sha256(i32 data_ptr, i32 data_len, i32 out32_ptr);

/* --- ledger (M3a packed struct) --- */
/* Encode a ledger artifact to canonical v2 bytes. The host packs the fields
 * into the input buffer below (little-endian; string = [u32 len][bytes];
 * strarray = [u32 count] then count strings):
 *   serial, intentVersionId, scopeStatement            ; 3x string
 *   workPerformed, laborRecorded, materialsUsed, contextualNotes ; 4x strarray (insertion order)
 *   totalCostCents, totalHoursCenti                     ; 2x i64 LE
 *   jobIds, timeEntryIds, chatMessageIds                ; 3x strarray (UNSORTED; core sorts)
 * Output is the canonical v2 form: "SMC" + 0x01 + 0x02, fields 1-9 verbatim,
 * then the three id arrays sorted ascending by unsigned utf-8 bytes. The
 * encoding format is byte-identical to the M2 host encoder (golden vectors).
 * Returns out_len, or SC_ERR. */
i32 sc_ledger_encode(i32 in_ptr, i32 in_len, i32 out_ptr, i32 out_cap);

/* --- entitlements (M4 packed bitmask) --- */
/* Encode the canonical entitlements record. Input (host-packed, little-endian):
 *   [u8 tierCode][u32 bitmask]
 * Output: [u8 format=0x01][u8 tierCode][u32 bitmask LE]  (6 bytes).
 * The host owns the tier->bits policy; the core owns the byte layout.
 * Returns out_len (6), or SC_ERR. */
i32 sc_entitlements_encode(i32 in_ptr, i32 in_len, i32 out_ptr, i32 out_cap);

#endif /* SMITHCORE_H */
