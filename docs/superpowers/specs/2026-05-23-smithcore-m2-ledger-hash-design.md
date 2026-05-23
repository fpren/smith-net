# SmithCore M2 — Ledger / Audit Hash Through the ROM

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Predecessors:** M1 (vclock + SHA-256 through the ROM), M1.5 (APK size-delta gate)
**Roadmap position:** M2 of M1 -> M1.5 -> **M2** -> M3 (mesh + packed structs) -> M4 (entitlements bitmask) -> M5 (portal/iOS/Pi shells)

---

## 0. North star

SmithCore is **ROM-like infrastructure**, inspired by the Game Boy cartridge model: ONE
deterministic core compiled to `smithcore.wasm` that every host loads UNCHANGED. The host
provides IO / time / keys; it never edits the ROM. The goal is a **small, simple, efficient,
deterministic** core — not a game, and not a framework. Every design choice below is held to
that bar: M2 adds **no new C**, **no dependencies**, and only **explicit bytes**.

The determinism moat (Intent -> SummaryArtifact -> LedgerEntry, SHA256-sealed, NFR-D1..D5)
requires the same bytes out for the same bytes in on every device. M2 brings the **ledger seal
hash** and the **audit-chain checksum** onto the one ROM SHA-256, and upgrades the ledger seal
to a versioned, drift-proof canonical encoding.

---

## 1. Scope

In scope:
1. **Ledger seal v2** — a new canonical byte encoding of the SummaryArtifact, hashed via the
   ROM's `sc_sha256`. Replaces the float-bearing `JSON.stringify` form for all *new* seals.
2. **Per-entry hash versioning** — `ledger_entries.hash_version`; verify dispatches on it so
   existing (v1) entries keep verifying.
3. **Audit chain through the ROM** — route both audit checksum sites through the ROM's SHA-256.
   Byte-identical to node crypto, so no audit version and no chain break.
4. **Cross-host proof** — mirror the v2 encoder in Kotlin and gate both hosts on a committed
   golden-vector fixture.

Non-goals (deferred to M3+), explicitly NOT built in M2:
- The C-side packed encoder (`sc_ledger_hash`). M2's encoder stays host-side (TS + Kotlin),
  exactly like the existing vclock codec.
- On-device ledger sealing in app flows. The Kotlin encoder exists to *prove parity* and be
  ready; nothing in the Android app calls it yet.
- Changing the audit canonical *body* (its free-form `metadata` stays JSON).
- Interning enums to integer codes, bitmask entitlements, far-pointer cross-references — these
  are pre-shaped below as M3/M4 direction, not implemented.

---

## 2. ROM-model principles adopted (design discipline, kept minimal)

From studying the real Game Boy cartridge format and the `pret/pokegold` disassembly (which
reproduces retail Gold/Silver bit-for-bit):

- **Self-describing fixed header.** Like the cartridge header at `0x100`, the v2 encoding leads
  with a tiny magic + ABI + format prefix, so the bytes identify themselves.
- **No floating bytes.** RGBDS distinguishes *fixed* sections (exact address) from *floating*
  (linker's discretion). Our review test for the whole moat: **for every byte in the canonical
  form, is its position/value FIXED by this spec, or FLOATING (host-decided)? Every floating
  byte is a determinism bug.** Map iteration order, JSON key order, float formatting are all
  "floating"; sorting, fixed field order, and integer minor units convert them to "fixed".
- **Two checksums, two jobs.** GB has a header checksum (verified by boot ROM at load,
  fail-closed) and a global checksum (whole-ROM integrity record). Our analogs stay distinct:
  the **load-time gate** (ABI check + `smithcore.wasm.sha256` stamp, already in M1) vs the
  **per-entry seal hash** (content integrity, checked on demand by verify).
- **Golden vectors as a committed truth file.** `pret` gates its build on `roms.sha1`. We
  commit a golden-vector fixture (artifact -> expected v2 hash) and gate both Jest and the
  Android instrumented test on it.
- **Two canonicalization strategies, chosen per data shape.** Variable sets (ids, vclock) ->
  **sort + omit-empty**. Fixed records (the scalar block) -> **fixed offset**. M2 uses both.

---

## 3. Ledger v2 canonical encoding

### 3.1 Header (self-describing, 5 bytes)

| bytes | field | value |
|---|---|---|
| 0..2 | magic | ASCII `"SMC"` (`0x53 0x4D 0x43`) |
| 3 | abi | `0x01` (matches `SC_VERSION`) |
| 4 | format | `0x02` (ledger canonical format v2; mirrors `hash_version`) |

### 3.2 Body — fixed field order (the declared layout registry)

Encoded immediately after the header, in exactly this order. This table is the single source
of truth; the TS encoder, the Kotlin encoder, and the golden fixture all conform to it.

| # | field | kind | encoding |
|---|---|---|---|
| 1 | serial | string | `[u32 len][utf8]` |
| 2 | intentVersionId | string | as above |
| 3 | scopeStatement | string | as above |
| 4 | workPerformed | string[] (insertion order) | `[u32 count]` + each string |
| 5 | laborRecorded | string[] (insertion order) | as above |
| 6 | materialsUsed | string[] (insertion order) | as above |
| 7 | contextualNotes | string[] (insertion order) | as above |
| 8 | totalCost | i64 cents | 8 bytes LE, two's complement |
| 9 | totalHours | i64 centihours | 8 bytes LE, two's complement |
| 10 | jobIds | string[] (sorted by utf8 bytes) | `[u32 count]` + each string |
| 11 | timeEntryIds | string[] (sorted) | as above |
| 12 | chatMessageIds | string[] (sorted) | as above |

Encoding primitives (all little-endian):
- `string` = `[u32 length][length bytes of UTF-8]`.
- `string[]` = `[u32 count]` then each element encoded as `string`.
- `i64` = 8 bytes, little-endian, two's complement.

### 3.3 Field-set rationale

v2 covers all **content** fields and all **synthesis-input ID** fields, closing the v1 gap
where `materialsUsed` / `contextualNotes` / `chatMessageIds` were unhashed (a tampered
materials list passed verify). Deliberately excluded:
- `createdAt` — a `Date.now()` clock read; hashing it would break reproducibility (D1/D3).
- `id` — the artifact UUID; the ledger entry references `artifactId` separately, and the hash
  is of content, not identity.

### 3.4 The float fix (cents / centihours)

`totalCost` and `totalHours` are produced already 2-decimal-quantized
(`Math.round(x*100)/100`) by the synthesizer. v2 hashes them as **integers**, never floats
(honoring the core's "no floating point" invariant and removing the only floating bytes in the
artifact):
- `cents = round(totalCost * 100)`
- `centihours = round(totalHours * 100)`

Rounding is defined as **round half up**: `floor(value*100 + 0.5)`. In Kotlin, `Math.round(Double): Long`
is exactly `floor(x + 0.5)`; the TS encoder uses `Math.round`. Because upstream values are
already cents-quantized, both hosts resolve any IEEE epsilon to the same integer. A parity test
feeds deliberately non-quantized values to assert the two hosts still agree.

### 3.5 Hash

`ledgerHashV2(artifact) = hex( ROM_sha256( header || body ) )`. The hash covers the whole
buffer including the 5-byte header.

---

## 4. Versioning and verify

- **Migration `020_ledger_hash_version.sql`:**
  `ALTER TABLE ledger_entries ADD COLUMN hash_version SMALLINT NOT NULL DEFAULT 1;`
  Existing rows default to 1; new seals insert 2.
- **`ledgerAuthority.ts` split:**
  - `computeHashV1(artifact)` — the exact current implementation, frozen (so legacy entries
    keep verifying byte-for-byte).
  - `computeHashV2(artifact)` — encode (Section 3) + ROM SHA-256.
  - `computeHashForVersion(artifact, version)` — dispatcher.
- **`ledger.ts`:** `seal()` and `amend()` compute v2 and persist `hash_version = 2`.
- **Verify path:** read `row.hash_version`, recompute with the matching function, compare.
  > Open item: `grep` did not locate an existing `/api/ledger/verify` route. The
  > determinism skill documents one. Planning step 1 confirms whether it exists (and updates
  > it to be version-aware) or adds it.

---

## 5. Audit chain (primitive swap only)

Both checksum sites compute `ROM_sha256( utf8( (prev ?? '') + body ) )` when the ROM is ready,
else node crypto:
- `auditLog.ts` `generateChecksum` (dev / in-memory path).
- `workers/auditFlushWorker.ts` `computeHash` (the pg / worker path).

Because the ROM's SHA-256 is byte-identical to node crypto over the same bytes (proven in M1),
output is unchanged: **existing chain stays valid, no `hash_version` for audit, no chain
break, `auditChain.test.ts` passes unchanged.** The body envelope is untouched.

The worker process gains `initSmithCore()` at boot (`workers/runner.ts`), mirroring
`server.ts`, so the worker path can use the ROM; if not ready it falls back to node crypto
(identical output).

---

## 6. Flag and rollout

`SMITHCORE_ENABLED === '1' && isSmithCoreReady()` gates *which SHA-256 engine* runs (ROM vs
node), exactly like `vectorClock.ts`. Both engines are byte-identical, so **the flag never
changes a stored hash** — it is a pure rollout lever, and the parity gate keeps proving the two
engines equal.

The v2 *encoding* is **unconditional** once M2 ships (all new seals are v2, stamped). Only the
SHA-256 engine is flag/readiness-gated, so sealing never blocks on the ROM.

---

## 7. Cross-host proof (what makes M2 the moat, not a refactor)

- **Kotlin mirror:** `android/.../core/LedgerCanon.kt` (plus a minimal artifact holder)
  implements the Section 3 encoding identically. No app wiring — on-device sealing is M3+.
- **Golden-vector fixture (the `roms.sha1` analog):** a committed file mapping a set of
  artifacts to their expected canonical hex and v2 hash. Consumed by:
  - backend Jest — asserts the TS encoder produces the golden bytes and `ledgerHashV2` ==
    golden hash;
  - `SmithCoreParityTest` (androidTest) — asserts the Kotlin encoder produces the **same**
    golden bytes.
  Together: **TS bytes == Kotlin bytes == golden**, and **ROM sha == golden hash**.

---

## 8. Tests / parity gate

Backend Jest (extend `__tests__/smithcore-parity.test.ts` or a sibling `ledger-hash` test):
- v2 encoder determinism (same artifact -> same bytes across runs).
- `ledgerHashV2 == sha256(encode) == ROM == node crypto`.
- golden vectors: encoder output and hash match the committed fixture.
- v1 frozen: `computeHashV1` still equals the pre-M2 hash for a fixed legacy artifact.
- rounding: non-quantized `totalCost`/`totalHours` resolve to the same cents/centihours.
- audit: ROM checksum == node checksum over the same seed (identity); chain unaffected.

Android instrumented (`SmithCoreParityTest`):
- Kotlin v2 encoder bytes == golden fixture (skips if ROM/WAMR not vendored, like M1).

---

## 9. Files touched

- **core/**: none (M2 reuses `sc_sha256`).
- **backend/src/**: `ledgerAuthority.ts` (v1/v2 split + dispatcher), `ledger.ts` (stamp
  version), `core/smithCore.ts` (`encodeLedgerArtifact` + `ledgerHashV2`), `auditLog.ts` +
  `workers/auditFlushWorker.ts` (ROM SHA-256), `workers/runner.ts` (init ROM), ledger verify
  route (locate/confirm/add), `__tests__/smithcore-parity.test.ts` (+ golden fixture).
- **backend/migrations/**: `020_ledger_hash_version.sql`.
- **android/**: `core/LedgerCanon.kt` (+ holder), `androidTest/.../core/SmithCoreParityTest.kt`.
- **docs/**: `core/README.md` status (M2 done), this spec.

---

## 10. M3 / M4 direction (pre-shaped, not built)

Recorded so M2's choices stay forward-compatible:
- **M3 packed structs** = GS fixed-size records: fixed offsets, **enum -> integer code** via an
  append-only registry (never renumber), sentinel-pad absent fields, **far-pointer**
  `(section_id, offset)` fixed-width cross-references. The C `sc_ledger_hash` targets the exact
  byte layout in Section 3.
- **M4 entitlements bitmask** = GS TM/HM learnset bitfield: fixed bit positions, append-only,
  never reuse a freed bit.

---

## 11. Risks / open items

1. **Verify route existence** (Section 4) — confirm in planning.
2. **Rounding edge cases** — mitigated by upstream quantization + explicit round-half-up + a
   parity test; if ever unsafe, the real fix is integer cents in `SummaryArtifact` (a schema
   change, out of M2 scope).
3. **v1 freeze fidelity** — `computeHashV1` must be a byte-exact copy of today's `computeHash`;
   a golden v1 vector locks it.
4. **Worker ROM init** — if `runner.ts` does not init the ROM, the worker silently uses node
   crypto (still correct, just not "through the ROM"); the test asserting identity makes this
   harmless, and a startup log records which engine is active.
