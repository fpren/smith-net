/*
 * entitlements.c -- canonical entitlements record (M4). A tiny packed struct:
 * input  [u8 tierCode][u32 bitmask LE]              (5 bytes, host-packed)
 * output [u8 format=0x01][u8 tierCode][u32 bitmask LE] (6 bytes)
 * The core owns the canonical byte layout; the host owns the tier->bits policy
 * (CAPS_BY_TIER). Freestanding, no libc.
 */
#include "core_internal.h"

i32 entitlements_encode(const u8 *in, i32 in_len, u8 *out, i32 out_cap) {
    if (in_len != 5) return SC_ERR;   /* u8 tierCode + u32 bitmask */
    if (out_cap < 6) return SC_ERR;
    out[0] = 0x01;                     /* entitlements record format v1 */
    for (i32 i = 0; i < 5; i++) out[1 + i] = in[i];
    return 6;
}
