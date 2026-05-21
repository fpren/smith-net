/*
 * sha256.c -- compact, public-domain-style SHA-256 (FIPS 180-4).
 *
 * Bundled INTO the ROM on purpose: hashing must be byte-identical on every
 * host, so the core must not depend on host crypto (OpenSSL, WebCrypto, Java
 * MessageDigest). This is the same primitive the TS Ledger (computeHash) and
 * the audit chain will delegate to in M2.
 */
#include "smithcore.h"

typedef struct {
    u32 h[8];
    u64 len;       /* total message length in bytes */
    u8  buf[64];
    u32 buflen;
} sha256_ctx;

static u32 rotr(u32 x, u32 n) { return (x >> n) | (x << (32 - n)); }

static const u32 K[64] = {
    0x428a2f98u,0x71374491u,0xb5c0fbcfu,0xe9b5dba5u,0x3956c25bu,0x59f111f1u,0x923f82a4u,0xab1c5ed5u,
    0xd807aa98u,0x12835b01u,0x243185beu,0x550c7dc3u,0x72be5d74u,0x80deb1feu,0x9bdc06a7u,0xc19bf174u,
    0xe49b69c1u,0xefbe4786u,0x0fc19dc6u,0x240ca1ccu,0x2de92c6fu,0x4a7484aau,0x5cb0a9dcu,0x76f988dau,
    0x983e5152u,0xa831c66du,0xb00327c8u,0xbf597fc7u,0xc6e00bf3u,0xd5a79147u,0x06ca6351u,0x14292967u,
    0x27b70a85u,0x2e1b2138u,0x4d2c6dfcu,0x53380d13u,0x650a7354u,0x766a0abbu,0x81c2c92eu,0x92722c85u,
    0xa2bfe8a1u,0xa81a664bu,0xc24b8b70u,0xc76c51a3u,0xd192e819u,0xd6990624u,0xf40e3585u,0x106aa070u,
    0x19a4c116u,0x1e376c08u,0x2748774cu,0x34b0bcb5u,0x391c0cb3u,0x4ed8aa4au,0x5b9cca4fu,0x682e6ff3u,
    0x748f82eeu,0x78a5636fu,0x84c87814u,0x8cc70208u,0x90befffau,0xa4506cebu,0xbef9a3f7u,0xc67178f2u
};

static void sha256_init(sha256_ctx *c) {
    c->h[0]=0x6a09e667u; c->h[1]=0xbb67ae85u; c->h[2]=0x3c6ef372u; c->h[3]=0xa54ff53au;
    c->h[4]=0x510e527fu; c->h[5]=0x9b05688cu; c->h[6]=0x1f83d9abu; c->h[7]=0x5be0cd19u;
    c->len = 0; c->buflen = 0;
}

static void sha256_block(sha256_ctx *c, const u8 *p) {
    u32 w[64];
    for (int i = 0; i < 16; i++)
        w[i] = ((u32)p[i*4] << 24) | ((u32)p[i*4+1] << 16) | ((u32)p[i*4+2] << 8) | (u32)p[i*4+3];
    for (int i = 16; i < 64; i++) {
        u32 s0 = rotr(w[i-15],7) ^ rotr(w[i-15],18) ^ (w[i-15] >> 3);
        u32 s1 = rotr(w[i-2],17) ^ rotr(w[i-2],19) ^ (w[i-2] >> 10);
        w[i] = w[i-16] + s0 + w[i-7] + s1;
    }
    u32 a=c->h[0],b=c->h[1],cc=c->h[2],d=c->h[3],e=c->h[4],f=c->h[5],g=c->h[6],h=c->h[7];
    for (int i = 0; i < 64; i++) {
        u32 S1 = rotr(e,6) ^ rotr(e,11) ^ rotr(e,25);
        u32 ch = (e & f) ^ ((~e) & g);
        u32 t1 = h + S1 + ch + K[i] + w[i];
        u32 S0 = rotr(a,2) ^ rotr(a,13) ^ rotr(a,22);
        u32 maj = (a & b) ^ (a & cc) ^ (b & cc);
        u32 t2 = S0 + maj;
        h=g; g=f; f=e; e=d+t1; d=cc; cc=b; b=a; a=t1+t2;
    }
    c->h[0]+=a; c->h[1]+=b; c->h[2]+=cc; c->h[3]+=d;
    c->h[4]+=e; c->h[5]+=f; c->h[6]+=g; c->h[7]+=h;
}

static void sha256_update(sha256_ctx *c, const u8 *data, u32 n) {
    c->len += n;
    while (n > 0) {
        u32 take = 64 - c->buflen;
        if (take > n) take = n;
        for (u32 i = 0; i < take; i++) c->buf[c->buflen + i] = data[i];
        c->buflen += take; data += take; n -= take;
        if (c->buflen == 64) { sha256_block(c, c->buf); c->buflen = 0; }
    }
}

static void sha256_final(sha256_ctx *c, u8 out[32]) {
    u64 bits = c->len * 8;
    u8 pad = 0x80;
    sha256_update(c, &pad, 1);
    u8 zero = 0x00;
    while (c->buflen != 56) sha256_update(c, &zero, 1);
    u8 lenbe[8];
    for (int i = 0; i < 8; i++) lenbe[i] = (u8)(bits >> (56 - i*8));
    sha256_update(c, lenbe, 8);
    for (int i = 0; i < 8; i++) {
        out[i*4]   = (u8)(c->h[i] >> 24);
        out[i*4+1] = (u8)(c->h[i] >> 16);
        out[i*4+2] = (u8)(c->h[i] >> 8);
        out[i*4+3] = (u8)(c->h[i]);
    }
}

void sc_sha256_raw(const u8 *data, u32 len, u8 out[32]) {
    sha256_ctx c;
    sha256_init(&c);
    sha256_update(&c, data, len);
    sha256_final(&c, out);
}
