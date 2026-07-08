# Design System v2 — Plan 3: Comm Mechanics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the spec §7 messaging set on both platforms — 7-minute sender coalescing with left-aligned grouped rows, unread grammar (bold name + amber badge + NEW divider + jump-to-latest), message actions (hover toolbar web / long-press SmithSheet Android: copy, delete, retry), per-message status microcopy (PENDING/SENT/FAILED/SEEN), Android read-receipt rendering, working web composer attachments, and MeshChip transport indicators.

**Architecture:** Web rebuilds MessageList's rows into a `MessageRow` component (sn tokens, dark-ready) fed by a pure `groupMessages` helper; sends become optimistic (client-generated id → PENDING → SENT/FAILED, `inject` already accepts `id`). Backend gains a `media` passthrough on `/api/messages/inject` so the existing `/api/media/upload` route becomes reachable from the composer. Android extends its existing 2-minute header grouping to the spec shape (7 min, avatar rail, soft bubbles, all left-aligned), activates the dormant `deliveryStatus` lifecycle (adds FAILED + retry), and wires the never-registered read-receipt listener into a StateFlow. New Android comm row visuals use `LocalSmithColors`/Tokens2 (the screen chrome stays v1 ConsoleTheme until Plan 4 — same transitional mix as the SmithConfirmDialogs already in these screens).

**Tech Stack:** React 18 + Zustand + Tailwind (sn tokens) + Vitest/RTL/MSW (portal); Express + multer (backend, existing routes); Jetpack Compose + Tokens2 + JUnit4/Turbine/coroutines-test (Android, JDK 17: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-08-design-system-v2-design.md` §4 (glyphs), §7 (messaging mechanics). Colors ONLY via tokens (`sn-*` web, `LocalSmithColors`/Tokens2 Android). No raw hex in UI code.
- Messages are LEFT-ALIGNED grouped rows on both platforms. No SMS right-alignment — own messages are distinguished by sender-name accent color, never by alignment.
- Coalescing window: same sender within **7 minutes** (`420_000` ms) groups under one avatar/header. New sender, gap > 7 min, or day change starts a new group.
- Status microcopy vocabulary: `PENDING / SENT / FAILED / SEEN`, JetBrains Mono (web `font-data`, Android `ConsoleTheme.jetBrainsMono`), 9-10px/sp, uppercase. FAILED renders in the attention token with tap-to-retry. **DELIVERED is reserved** (the type unions include it) but never rendered — no delivery signal exists on any layer; building delivery acks is out of scope (tracked, §11-adjacent).
- Unread grammar: bold channel/conversation name + attention-amber count badge; amber `NEW` divider at first unread; jump-to-latest pill when scrolled up. Amber = `sn-attention` / `colors.attention` — the accent (cobalt/gold) never badges unread.
- Glyphs per `design/GLYPHS.md` only. Media affordances use registry glyphs `[▣]` photo, `[▶]` voice/play, `[≡]` file. Any NEW glyph must be added to the registry in the same task that first renders it (this plan adds `↓` jump-to-latest). No emoji anywhere.
- Message action order is identical on both platforms: copy, delete, retry (failed only). (Reply is post-v2.)
- Android: existing swipe actions (archive / delete-for-me / delete-for-all) STAY — long-press SmithSheet is additive. `solo_e2e_*` testTags and Maestro-pinned visible text survive: grep `android/maestro/*.yaml` before renaming any user-visible string.
- Preserve web read-receipt sending (`wsClient.sendReadReceipt`, once per message id) and Android typing wiring exactly as they work today.
- No Material widgets in new Android code; `androidx.compose.material3.Text` allowed.
- Commit style: `type(scope): summary`, body ends `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Repo root `/Users/fegensprenelon/smith-net`. Portal commands from `desktop/portal`; backend from `backend`; Android gradle from `android` with the JDK 17 export.

---

### Task 1: Backend media passthrough on message inject

**Files:**
- Modify: `backend/src/types.ts` (Message interface ~lines 37-47; InjectMessageRequest ~161-165)
- Modify: `backend/src/messageStore.ts` (`add`, ~lines 20-43)
- Modify: `backend/src/channelsRoutes.ts` (`POST /api/messages/inject`, ~lines 328-423)
- Test: extend the existing backend test suite for message routes (locate the suite that logs in via `POST /api/auth/login` — the WS auth test added in the CI fix uses this pattern; put the new cases beside the closest messages/channels route test, or create one following that file's setup verbatim)

**Interfaces:**
- Consumes: nothing new.
- Produces: `POST /api/messages/inject` accepts optional `media` object `{ type: 'image'|'voice'|'video'|'file', url: string, filename?, mimeType?, size?, duration?, thumbnailUrl? }`; the stored message, the 201 response, and the WS `message` broadcast all carry `media` verbatim. Web Task 4 depends on this exact shape (it matches the portal's existing `MediaAttachment` type at `desktop/portal/src/types.ts:20-30`).

- [ ] **Step 1: Write the failing test** — two cases: (a) inject with a valid `media` object → 201, response body includes the same `media`; (b) inject with `media.url` not starting with `/media/` and not `http` → 400. Use the suite's existing auth+channel fixtures.

```ts
it('carries a media attachment through inject', async () => {
  const res = await agent
    .post('/api/messages/inject')
    .set('Authorization', `Bearer ${token}`)
    .send({
      channelId,
      content: '[▣] photo',
      media: { type: 'image', url: '/media/images/abc.jpg', mimeType: 'image/jpeg', size: 1234 },
    });
  expect(res.status).toBe(201);
  expect(res.body.media).toEqual(
    expect.objectContaining({ type: 'image', url: '/media/images/abc.jpg' }),
  );
});

it('rejects media with a non-local, non-http url', async () => {
  const res = await agent
    .post('/api/messages/inject')
    .set('Authorization', `Bearer ${token}`)
    .send({ channelId, content: 'x', media: { type: 'file', url: 'javascript:alert(1)' } });
  expect(res.status).toBe(400);
});
```

- [ ] **Step 2: Run the backend suite to verify FAIL** (media echoed back is undefined; 400 case returns 201)
- [ ] **Step 3: Implement.** In `types.ts` add to `Message`:

```ts
media?: {
  type: 'image' | 'voice' | 'video' | 'file';
  url: string;
  filename?: string;
  mimeType?: string;
  size?: number;
  duration?: number;
  thumbnailUrl?: string;
};
```

and `media?: Message['media']` to `InjectMessageRequest`. In `messageStore.ts` give `add` a trailing optional param `media?: Message['media']` and set it on the constructed message. In `channelsRoutes.ts` inject handler: read `media` from the body; if present validate `typeof media.url === 'string' && (media.url.startsWith('/media/') || media.url.startsWith('http'))` and `['image','voice','video','file'].includes(media.type)`, else `return res.status(400).json({ error: 'invalid media' })`; pass it as the new `messageStore.add` argument. The 201 response spreads the stored message, so `media` flows automatically; confirm the WS broadcast path sends the stored message object (it does — `broadcastToChannel` receives the message from the store) and therefore needs no change.

- [ ] **Step 4: Run backend suite → PASS** (all pre-existing tests stay green; the two new ones pass)
- [ ] **Step 5: Commit**

```bash
git add backend/src/types.ts backend/src/messageStore.ts backend/src/channelsRoutes.ts backend/src
git commit -m "feat(backend): message inject carries media attachments

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Web optimistic send + message status machinery

**Files:**
- Modify: `desktop/portal/src/types.ts` (Message interface, lines 32-43)
- Modify: `desktop/portal/src/console/stores/commStore.ts` (add `updateMessage`)
- Modify: `desktop/portal/src/console/api/commClient.ts` (`send`, lines 92-103 — accept optional `id` and `media`)
- Modify: `desktop/portal/src/console/components/comm/MessageInput.tsx` (optimistic send, lines 50-71)
- Test: `desktop/portal/src/console/stores/__tests__/commStore.test.ts` (extend), `desktop/portal/src/console/components/comm/__tests__/MessageInput.test.tsx` (create)

**Interfaces:**
- Consumes: `POST /api/messages/inject` accepting `id` (already true) and `media` (Task 1).
- Produces: `types.ts` gains `export type MessageStatus = 'pending' | 'sent' | 'delivered' | 'failed' | 'seen';` and `Message.status?: MessageStatus` (client-side only; absent means settled/sent). `commStore` gains `updateMessage(channelId: string, messageId: string, patch: Partial<Message>): void` (merges into the matching message, no-op if missing; re-sorts by timestamp after patch). `commClient.send(channelId, content, opts?: { id?: string; media?: MediaAttachment })` posts `{ channelId, content, id, media }`. `MessageInput` gains exported helper `sendOptimistic` used by Task 4. Retry contract for Task 3: a failed message keeps its id; retry = `updateMessage(status:'pending')` then re-`send` with the same id.

- [ ] **Step 1: Write the failing tests.** Store: `updateMessage` merges a patch and ignores unknown ids. Component (`MessageInput.test.tsx`, follow `MessageList.test.tsx`'s render/MSW pattern):

```tsx
it('appends a pending message immediately and settles to sent', async () => {
  // MSW default inject handler returns 201 echoing the message
  render(<MessageInput />);            // wrap with the same providers MessageList.test uses
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'hello crew' } });
  fireEvent.click(screen.getByRole('button', { name: /send/i }));
  // pending appears synchronously
  const msgs = () => useCommStore.getState().messagesByChannel['ch-general'] ?? [];
  expect(msgs().some((m) => m.content === 'hello crew' && m.status === 'pending')).toBe(true);
  await waitFor(() => expect(msgs().some((m) => m.content === 'hello crew' && m.status === 'sent')).toBe(true));
});

it('marks the message failed when inject 500s', async () => {
  server.use(http.post('/api/messages/inject', () => HttpResponse.json({ error: 'x' }, { status: 500 })));
  render(<MessageInput />);
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'doomed' } });
  fireEvent.click(screen.getByRole('button', { name: /send/i }));
  await waitFor(() => {
    const m = useCommStore.getState().messagesByChannel['ch-general']?.find((x) => x.content === 'doomed');
    expect(m?.status).toBe('failed');
  });
});
```

Adapt selector/provider details to how `MessageInput` actually mounts (it reads the selected channel from the store — set `selectedChannelId` in the test setup exactly as `MessageList.test.tsx` does).

- [ ] **Step 2: Run to verify FAIL**
- [ ] **Step 3: Implement.**
  - `types.ts`: add the `MessageStatus` union + `status?` field.
  - `commStore.ts`:

```ts
updateMessage: (channelId, messageId, patch) =>
  set((state) => {
    const list = state.messagesByChannel[channelId];
    if (!list?.some((m) => m.id === messageId)) return state;
    const next = list
      .map((m) => (m.id === messageId ? { ...m, ...patch } : m))
      .sort((a, b) => a.timestamp - b.timestamp);
    return { messagesByChannel: { ...state.messagesByChannel, [channelId]: next } };
  }),
```

  - `commClient.send(channelId, content, opts)`: include `id: opts?.id` and `media: opts?.media` in the POST body when set; return shape unchanged.
  - `MessageInput.tsx` send flow (self identity comes from the same source `MessageList` reads `selfId` from):

```ts
const tempId = crypto.randomUUID();
const optimistic: Message = {
  id: tempId, channelId, senderId: selfId, senderName: selfName,
  content: trimmed, timestamp: Date.now(), origin: 'online', status: 'pending',
};
useCommStore.getState().appendMessage(optimistic);
setText('');
const result = await commClient.send(channelId, trimmed, { id: tempId }).catch(() => ({ ok: false as const }));
if (result.ok) {
  useCommStore.getState().updateMessage(channelId, tempId, { ...result.message, status: 'sent' });
} else {
  useCommStore.getState().updateMessage(channelId, tempId, { status: 'failed' });
}
```

  The `.catch` guard closes the existing unguarded-reject gap. `appendMessage` already dedupes by id, so the WS echo of the same message is dropped — the settled status is never clobbered.

- [ ] **Step 4: Run the two test files, then the full suite → PASS**
- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/types.ts desktop/portal/src/console/stores desktop/portal/src/console/api/commClient.ts desktop/portal/src/console/components/comm
git commit -m "feat(portal): optimistic sends with pending/sent/failed status

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Web MessageRow — grouping, status microcopy, MeshChip, media, hover actions

**Files:**
- Create: `desktop/portal/src/console/components/comm/messageGrouping.ts`
- Create: `desktop/portal/src/console/components/comm/MessageRow.tsx`
- Modify: `desktop/portal/src/console/components/comm/MessageList.tsx` (rows replaced by MessageRow; delete/confirm state stays here)
- Test: `desktop/portal/src/console/components/comm/__tests__/messageGrouping.test.ts` (create), `__tests__/MessageRow.test.tsx` (create), `__tests__/MessageList.test.tsx` (update selectors)

**Interfaces:**
- Consumes: `Message.status` (Task 2), `Message.origin`, `Message.media`, `readByMessage` from commStore, `Avatar` from `../ui/Avatar`, `accentForId`/`initials` utilities already used by ActivityRow.
- Produces:
  - `groupMessages(messages: Message[]): Array<{ message: Message; firstOfGroup: boolean }>` — pure; `firstOfGroup` true when index 0, sender changes, gap > `420_000` ms, or calendar day changes.
  - `MessageRow({ message, firstOfGroup, mine, seenByOthers, onDelete, onRetry })` — ALWAYS left-aligned. First-of-group: 24px `Avatar` + sender name (`text-sn-accent` when `mine`, `text-sn-ink` otherwise, `font-semibold text-xs`) + `HH:MM` (`font-data text-[10px] text-sn-ink-muted`) header row, bubble below. Grouped: bubble only, indented by the avatar column width. Bubble: `rounded-[14px] bg-sn-bg-sunken text-sn-ink px-3 py-2 text-sm max-w-[75ch] whitespace-pre-wrap break-words` (same surface for mine/others — the name color differentiates).
  - Status microcopy (mine only), right of the bubble footer: `font-data text-[10px] uppercase`; precedence `failed > pending > seen > sent`; `seen` when `seenByOthers > 0`. FAILED renders `text-sn-attention` as a button labeled `FAILED · RETRY` calling `onRetry`.
  - MeshChip inline in the header row when `origin !== 'online'`: `<span className="font-data text-[9px] uppercase border border-sn-accent text-sn-accent rounded-full px-1.5">mesh</span>` (gateway/online+mesh count as mesh-delivered).
  - Media block inside the bubble when `message.media` is set: `image` → `<img src={media.url} className="max-h-64 rounded-[10px]" alt={media.filename ?? 'photo'} />`; `voice` → `[▶] {duration}s` link; `file`/`video` → `[≡] {filename}` link (`<a href={media.url} target="_blank" rel="noreferrer">`, `font-data text-xs underline`). Glyphs render in the mono font per registry rules (they sit inside `font-data` spans).
  - Hover toolbar (`opacity-0 group-hover:opacity-100`, top-right of the row): order copy → delete → retry. Copy always (`navigator.clipboard.writeText(message.content)`, glyphless text button `copy`); delete only when `mine` (moves the existing `[x]` affordance here, same `aria-label="Delete message"`, still confirm-gated in MessageList); retry only when `status === 'failed'`.
- Retry wiring in MessageList: `onRetry = (m) => { updateMessage(m.channelId, m.id, { status: 'pending' }); void commClient.send(m.channelId, m.content, { id: m.id, media: m.media }).then((r) => updateMessage(m.channelId, m.id, r.ok ? { ...r.message, status: 'sent' } : { status: 'failed' })); }`.

- [ ] **Step 1: Write failing grouping tests** (pure function — exhaustive):

```ts
const msg = (id: string, sender: string, ts: number): Message => ({
  id, channelId: 'c', senderId: sender, senderName: sender, content: id, timestamp: ts, origin: 'online',
});
it('groups same sender within 7 minutes', () => {
  const out = groupMessages([msg('a', 'u1', 0), msg('b', 'u1', 6 * 60_000)]);
  expect(out.map((r) => r.firstOfGroup)).toEqual([true, false]);
});
it('breaks on sender change, on >7min gap, and on day change', () => {
  const dayMs = 24 * 60 * 60 * 1000;
  const out = groupMessages([
    msg('a', 'u1', 0), msg('b', 'u2', 1000),
    msg('c', 'u2', 1000 + 7 * 60_000 + 1), msg('d', 'u2', dayMs + 1000),
  ]);
  expect(out.map((r) => r.firstOfGroup)).toEqual([true, true, true, true]);
});
```

- [ ] **Step 2: Write failing MessageRow tests**: renders avatar+name for firstOfGroup and not for grouped; every row is left-aligned (no `items-end` in the container class); FAILED shows `FAILED · RETRY` and clicking calls onRetry; mesh origin shows the mesh chip; image media renders an img with the url; copy button writes to a mocked clipboard.
- [ ] **Step 3: Run to verify FAIL, implement `messageGrouping.ts` + `MessageRow.tsx` per the Produces contract, rewrite MessageList's map to `groupMessages(messages).map(...)`** — keep: read-receipt sending effect, near-bottom autoscroll, typing indicator, stale banner, ConfirmDialog delete flow (now triggered from the toolbar). Remove: the `mine` right-alignment (`items-end`, `flex-row-reverse`, accent bubble classes), the old inline `[x]` button, the old `seen {n}` text (superseded by SEEN microcopy).
- [ ] **Step 4: Update `MessageList.test.tsx`** — the delete test now hovers/queries the toolbar button (same aria-label); add an assertion that own messages are NOT right-aligned (container lacks `items-end`).
- [ ] **Step 5: Full suite + build → PASS. Commit**

```bash
git add desktop/portal/src/console/components/comm
git commit -m "feat(portal): grouped left-aligned MessageRow with status, mesh chip, media, hover actions

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Web composer attachments wired to /api/media

**Files:**
- Modify: `desktop/portal/src/console/components/comm/MessageInput.tsx` (wire the inert `[+]`, lines ~93-100)
- Modify: `desktop/portal/src/console/api/commClient.ts` (add `uploadMedia`)
- Modify: `desktop/portal/src/console/test/msw-handlers.ts` (add `/api/media/upload` handler)
- Test: extend `__tests__/MessageInput.test.tsx`

**Interfaces:**
- Consumes: Task 1's inject `media` field; Task 2's `sendOptimistic` flow and `commClient.send(..., { id, media })`; backend `POST /api/media/upload` (multipart, field `file`, body fields `messageId`, `channelId`, `senderId`, optional `mediaType` of `IMAGE|VOICE|FILE`; 201 → `{ id, url, filename, size, mimeType }`).
- Produces: `commClient.uploadMedia(file: File, messageId: string, channelId: string, senderId: string): Promise<{ ok: true; url: string; filename?: string; size?: number; mimeType?: string } | { ok: false }>` — FormData POST, `mediaType` derived from `file.type` (`image/*` → IMAGE, `audio/*` → VOICE, else FILE). The `[+]` button opens a hidden `<input type="file">`; picking a file runs: generate `tempId` → append optimistic message (`content` = `''`, `status: 'pending'`, `media: { type, url: blobUrl, filename: file.name }` with `URL.createObjectURL` for instant preview) → `uploadMedia` → on ok, `send(channelId, content, { id: tempId, media: { type, url, filename, mimeType, size } })` → settle to `sent` with the server media; on either failure settle to `failed` (retry re-runs from the upload step using the kept `File` in a ref keyed by tempId).
- Attachment `type` mapping for the message: `image/*` → `'image'`, `audio/*` → `'voice'`, `video/*` → `'video'`, else `'file'`.

- [ ] **Step 1: Add the MSW handler** (echoes a deterministic url):

```ts
http.post('/api/media/upload', async () =>
  HttpResponse.json(
    { id: 'media-1', url: '/media/images/media-1.jpg', filename: 'photo.jpg', size: 100, mimeType: 'image/jpeg' },
    { status: 201 },
  ),
),
```

- [ ] **Step 2: Write failing tests**: picking a file appends a pending message with media immediately; it settles to `sent` with the server url; a 500 from `/api/media/upload` settles it to `failed`. Use `fireEvent.change(fileInput, { target: { files: [new File(['x'], 'photo.jpg', { type: 'image/jpeg' })] } })`.
- [ ] **Step 3: Implement per the Produces contract; run tests → PASS; full suite green**
- [ ] **Step 4: Commit**

```bash
git add desktop/portal/src/console
git commit -m "feat(portal): composer [+] uploads media and sends attachments

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Web unread grammar — bold + amber badge, NEW divider, jump-to-latest

**Files:**
- Modify: `design/GLYPHS.md` (register `↓` — meaning "jump to latest", context: comm scroll pill only)
- Modify: `desktop/portal/src/console/components/comm/ActivityRow.tsx` (badge + bold, lines ~60-70)
- Modify: `desktop/portal/src/console/stores/commStore.ts` (NEW-divider anchor)
- Modify: `desktop/portal/src/console/components/comm/MessageList.tsx` (divider + pill)
- Test: extend `commStore.test.ts`, `MessageList.test.tsx`; create `__tests__/ActivityRow.test.tsx` if none exists

**Interfaces:**
- Consumes: `unreadByChannel`, `selectChannel` (zeroes unread at lines ~67-74).
- Produces:
  - ActivityRow: title `font-bold` when `unread > 0` (else the current weight); count pill becomes `bg-sn-attention text-sn-ink-on-accent` (amber badges unread — the gold/accent pill dies).
  - commStore: `unreadAtSelect: Record<string, number>` — in `selectChannel`, BEFORE zeroing, snapshot `unreadAtSelect[channelId] = unreadByChannel[channelId] ?? 0`. Selecting a different channel clears the previous channel's snapshot.
  - MessageList NEW divider: on the first render after messages load for the selected channel, if `unreadAtSelect > 0`, the divider index = `messages.length - unreadAtSelect` (clamped ≥ 0, frozen in a ref until channel changes so late arrivals don't move it). Divider row: full-width hairline `border-sn-attention` with centered label `NEW` (`font-data text-[10px] uppercase text-sn-attention bg-sn-bg-base px-2`).
  - Jump-to-latest pill: shown when the list is scrolled up more than ~300px from the bottom; fixed near the bottom of the scroll area: `↓ latest` (`font-data text-xs`, `bg-sn-accent text-sn-ink-on-accent rounded-full px-3 py-1 shadow-sn-sm`); click smooth-scrolls to the end. Reuses the existing near-bottom tracking (lines ~52-59) — extend it to expose "distance from bottom" state instead of a boolean only.

- [ ] **Step 1: Write failing tests.** Store: `selectChannel` snapshots `unreadAtSelect` before zeroing. MessageList: with `unreadAtSelect = 2` and 5 messages, a `NEW` divider renders before the 4th message; with 0 it does not render. ActivityRow: unread row has bold title and an element with `bg-sn-attention`.
- [ ] **Step 2: Verify FAIL, implement, verify PASS + full suite**
- [ ] **Step 3: Add `↓` to `design/GLYPHS.md`** with allowed context "comm jump-to-latest pill only" and never-use "not a scroll indicator elsewhere; not a sort marker".
- [ ] **Step 4: Commit**

```bash
git add design/GLYPHS.md desktop/portal/src/console
git commit -m "feat(portal): unread grammar - amber badge, NEW divider, jump-to-latest

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Android message status lifecycle (PENDING/SENT/FAILED + retry)

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/data/Message.kt` (enum, ~line 21)
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/db/MessageEntity.kt` (toMessage mapping, ~line 68)
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/data/MessageRepository.kt` (status update API)
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ConversationViewModel.kt` (`sendMessage` ~line 151; add `retryMessage`)
- Test: `android/app/src/test/java/com/guildofsmiths/trademesh/data/MessageRepositoryStatusTest.kt` (create; Robolectric if Room is touched, else pure JUnit with the in-memory flow)

**Interfaces:**
- Consumes: `MessageEntity.DeliveryStatus` int constants (already include `FAILED = -1`).
- Produces:
  - Domain enum becomes `enum class DeliveryStatus { PENDING, SENT, DELIVERED, READ, FAILED }` (DELIVERED/READ stay for compatibility; READ is rendered as SEEN microcopy in Task 8).
  - `MessageEntity.toMessage()` maps the int column to the domain enum (`-1 → FAILED`, `0 → PENDING`, `1 → SENT`, `2 → DELIVERED`, `3 → READ`) — persisted status stops being dropped on load. `Message.toEntity()` (or wherever the entity is built) maps the reverse.
  - `MessageRepository.updateDeliveryStatus(messageId: String, status: DeliveryStatus)` — updates the in-memory `_allMessages` StateFlow entry AND calls the existing (currently caller-less) DAO `updateDeliveryStatus` with the int mapping.
  - `ConversationViewModel.sendMessage`: the outgoing message is created with `deliveryStatus = DeliveryStatus.PENDING`; after `BoundaryEngine.routeMessage(...)` returns without throwing → `updateDeliveryStatus(id, SENT)`; on exception → `updateDeliveryStatus(id, FAILED)`. (SENT = handed to transport; true DELIVERED needs acks and is out of scope.)
  - `ConversationViewModel.retryMessage(messageId: String)`: looks up the message, sets PENDING, re-runs the same route call, settles SENT/FAILED identically. Task 9's sheet calls this.

- [ ] **Step 1: Write the failing repository test** (Turbine on `_allMessages`-derived flow): add a PENDING message, `updateDeliveryStatus(id, SENT)` → emitted list has SENT; `updateDeliveryStatus(id, FAILED)` → FAILED; unknown id is a no-op.
- [ ] **Step 2: Run `./gradlew :app:testDebugUnitTest --tests "*MessageRepositoryStatusTest*"` → FAIL (method missing)**
- [ ] **Step 3: Implement the four Produces items.** Keep every existing `Message(...)` construction compiling — the enum only gains a case; the default stays `SENT` for incoming paths so mesh/WS arrivals are unaffected.
- [ ] **Step 4: Full `:app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL**
- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh android/app/src/test
git commit -m "feat(android): delivery status lifecycle with FAILED and retry

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Android read receipts — collect, emit, expose

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/data/MessageRepository.kt` (receipt state + listener registration)
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt` (emit receipts for viewed incoming messages)
- Test: `android/app/src/test/java/com/guildofsmiths/trademesh/data/ReadReceiptsTest.kt` (create)

**Interfaces:**
- Consumes: `ChatManager.setReadReceiptListener` / `OnReadReceiptListener(messageId, readBy, readAt)` (`service/ChatManager.kt:93-101`, currently zero callers) and `ChatManager.sendReadReceipt(messageId, channelId)` (`:495-504`, zero callers).
- Produces:
  - `MessageRepository.readByMessage: StateFlow<Map<String, Set<String>>>` — messageId → userIds that read it. Populated by registering the ChatManager listener in the repository's init (self-reads excluded). Exposed function `markReadLocal(messageId, userId)` used by the listener and testable directly.
  - ConversationScreen: a `LaunchedEffect(messages)` that, for incoming messages (`senderId != localUserId`) not yet receipted (a `remember`ed `MutableSet<String>` of sent ids), calls `ChatManager.sendReadReceipt(message.id, channelId)` — mirror of the web's once-per-id pattern.
  - Own-message SEEN: Task 8 renders SEEN when `readByMessage[message.id]` minus self is non-empty.

- [ ] **Step 1: Write the failing test**: `markReadLocal` accumulates users per message and excludes duplicates; the flow emits on change (Turbine).
- [ ] **Step 2: FAIL → implement → PASS; compile clean**
- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh android/app/src/test
git commit -m "feat(android): read receipts collected into repository state and emitted on view

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: Android MessageRow redesign — 7-min groups, left-aligned bubbles, microcopy, MeshChip

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt` (`MessageBlock` ~842, `shouldShowHeader` ~1104, swipe container ~433, status microcopy ~887-902)

**Interfaces:**
- Consumes: `LocalSmithColors`/Tokens2 (`RadiusBubble` = 14dp), `SmithAvatar` (`ui/components/SmithAvatar.kt`), `DeliveryStatus` incl FAILED (Task 6), `readByMessage` (Task 7), `Message.isMeshOrigin`, `ConsoleTheme.jetBrainsMono` + `ConsoleTheme.inter`.
- Produces, inside `MessageBlock`:
  - `shouldShowHeader` window `120_000` → `420_000` (7 min); keep the day-change break (`isDifferentDay` already feeds `DateSeparator` — grouping must ALSO break on day change).
  - ALL rows `Arrangement.Start` — delete the `isSentByMe` End branch. Own messages keep the accent-colored sender name; the `▶`/`◀` prefix glyphs are removed (alignment prefixes made sense for a two-sided layout; the registry keeps `▶` for voice/play only).
  - First-of-group rows: `SmithAvatar(name = message.senderName, size = 28)` in a left rail + name (`ConsoleTheme.inter` SemiBold 13sp; color `colors.accent` if own, `colors.ink` otherwise) + `HH:mm` (`jetBrainsMono` 10sp, `colors.inkMuted`) + MeshChip when `isMeshOrigin`. Grouped rows: bubble only, start-padded by the rail width (28.dp + gap).
  - Bubble: `Box(Modifier.clip(RoundedCornerShape(Tokens2.RadiusBubble)).background(colors.bgSunken).padding(horizontal = 12.dp, vertical = 8.dp))`, content `ConsoleTheme.commBody` in `colors.ink`. The 2dp `sentLine` marker dies.
  - Status microcopy (own messages, under the bubble): `jetBrainsMono` 9sp uppercase — `PENDING` / `SENT` (`colors.inkMuted`), `SEEN` (`colors.statusOnline`) when Task 7's set minus self is non-empty (takes precedence over SENT/DELIVERED/READ), `FAILED · TAP TO RETRY` (`colors.attention`) clickable → `retryMessage(message.id)`. The `[✓]`/`[✓✓]` glyphs die (unregistered).
  - MeshChip: `Text("MESH", jetBrainsMono 9sp, colors.accent)` in a `border(1.dp, colors.accent, RoundedCornerShape(999.dp)).padding(horizontal = 6.dp, vertical = 1.dp)`. Replaces the `[sub]`/`[online]` text pair.
  - Swipe container: replace the fixed `Box(height = 60.dp)` with intrinsic height — `Modifier.height(IntrinsicSize.Min)` on the swipe Box with the reveal backgrounds on `Modifier.matchParentSize()` — so tall/media messages stop clipping. Swipe actions, thresholds, and callbacks are otherwise UNTOUCHED.
- Keep: `DateSeparator`, typing indicator, DM selector, composer, `[queued]`/`[DM]` markers (registry-compatible ASCII), auto-scroll effect.

- [ ] **Step 1: Implement per the contract** (UI-only task; the compile + existing unit tests are the gate — there is no Compose UI test rig in this repo)
- [ ] **Step 2: Grep guard** — from `android/`: `grep -n "Arrangement.End" app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt` → no message-row hits (composer row may legitimately keep End); `grep -rn "✓" app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt` → zero.
- [ ] **Step 3: `:app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL; commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui
git commit -m "feat(android): left-aligned 7-min grouped message rows with status microcopy and mesh chip

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: Android long-press message action sheet

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt` (long-press + sheet)

**Interfaces:**
- Consumes: `SmithSheet(onDismiss, content)` (theme2), `MessageAction` enum + `onMessageAction` callback (existing, wired to `ConversationViewModel.handleMessageAction`), `retryMessage` (Task 6), Android `ClipboardManager` (`LocalClipboardManager.current`).
- Produces: `Modifier.combinedClickable(onClick = {}, onLongClick = { actionTarget = message })` on the message row (requires `@OptIn(ExperimentalFoundationApi::class)`); `actionTarget: Message?` state; when non-null, a `SmithSheet` with rows in the spec's order:
  1. `COPY` — `clipboard.setText(AnnotatedString(message.content))`, dismiss.
  2. `DELETE FOR ME` — `onMessageAction(message, MessageAction.DELETE_FOR_ME)`, dismiss.
  3. `DELETE FOR EVERYONE` — only when the same `canDeleteForAll && isSentByMe` condition the swipe path uses; same action constant.
  4. `RETRY` — only when `deliveryStatus == FAILED`; calls `retryMessage(message.id)`, dismiss.
  Rows: full-width `Text` in `ConsoleTheme.inter` Medium 14sp, `colors.ink` (delete rows `colors.statusError`), `clickable`, 12.dp vertical padding — matching the SmithSheet usage pattern in `ui/expenses/InvoicePreviewBottomSheet.kt`.
- Swipe actions remain untouched alongside.

- [ ] **Step 1: Implement; verify long-press does not break the existing `draggable` swipe (combinedClickable and draggable compose cleanly on the same node — test manually via compile + reasoning; note any gesture conflict in the report)**
- [ ] **Step 2: `:app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL; commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui
git commit -m "feat(android): long-press message sheet - copy, delete, retry

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 10: Android unread grammar — amber badge, bold name, NEW divider, jump-to-latest

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ChatListScreen.kt` (`ChatRow` ~643, `UnreadBadge` ~729/790)
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ConversationViewModel.kt` (`setChannel` ~112 — snapshot before clear)
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt` (divider + pill)

**Interfaces:**
- Consumes: `Channel.unreadCount`, `BeaconRepository.clearUnread`, `LocalSmithColors.attention`, `listState` (existing `rememberLazyListState`).
- Produces:
  - `UnreadBadge` fill: `ConsoleTheme.accent` → `LocalSmithColors.current.attention`, count text in `colors.inkOnAccent`. ChatRow title: existing `bodyBold` stays for read rows is wrong — spec wants differentiation: read rows drop to `ConsoleTheme.body` weight, unread rows keep `bodyBold`. (Check Maestro yaml for pinned strings first; weight changes are safe, string changes are not.)
  - `ConversationViewModel`: `unreadAtOpen: Int` snapshot captured in `setChannel` BEFORE `clearUnread` (exposed as immutable state for the screen).
  - ConversationScreen NEW divider: when `unreadAtOpen > 0`, divider before item index `messages.size - unreadAtOpen` (clamped, frozen at first composition for the channel): hairline `colors.attention` + centered `NEW` (`jetBrainsMono` 10sp uppercase, `colors.attention`).
  - Jump-to-latest pill: visible when `listState.firstVisibleItemIndex < messages.size - 8` (scrolled well above the tail); bottom-center overlay above the composer: `↓ LATEST` (`jetBrainsMono` 11sp, `colors.inkOnAccent` on `colors.accent`, pill shape, `clickable { scope.launch { listState.animateScrollToItem(messages.size - 1) } }`). `↓` is registry-legal after Task 5's GLYPHS.md addition.

- [ ] **Step 1: Implement all four; grep the Maestro yaml for any pinned chat-list strings before touching ChatRow**
- [ ] **Step 2: `:app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL; commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh
git commit -m "feat(android): unread grammar - amber badge, NEW divider, jump-to-latest

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 11: Whole-plan verification gates

**Files:** none (verification only; a gate failure is fixed in its owning task's file scope with a `fix(...)` commit).

- [ ] **Step 1: Portal** — `cd desktop/portal && npx vitest run && npm run build` → green.
- [ ] **Step 2: Backend** — run the backend test suite (same command backend CI uses) → green.
- [ ] **Step 3: Android** — JDK-17 gradle `:app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 4: Tokens + glyphs** — `node scripts/gen-tokens.mjs --check` → up to date; `design/GLYPHS.md` contains the `↓` entry; `grep -rn "✓" desktop/portal/src/console/components/comm android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt` → zero (no unregistered glyphs).
- [ ] **Step 5: Alignment guards** — portal: `grep -n "items-end\|flex-row-reverse" desktop/portal/src/console/components/comm/MessageList.tsx desktop/portal/src/console/components/comm/MessageRow.tsx` → zero.
- [ ] **Step 6: Report all gate outputs in the task report.**

---

## Self-Review

- Spec §7 coverage: coalescing 7-min left-aligned (T3 web / T8 Android); unread grammar incl NEW divider + jump pill (T5 / T10); actions copy-delete-retry hover/long-press (T3 / T9); status microcopy with FAILED-retry (T2+T3 / T6+T8); Android receipts rendered (T7+T8); web attachments working (T1+T4); MeshChip both platforms (T3 / T8); transport vocabulary untouched (ConnectionStatusBar already speaks ONLINE/MESH/OFFLINE). DELIVERED explicitly reserved — no delivery-ack signal exists on any layer; noted for a future backend plan. Reply actions are post-v2 per spec.
- Type consistency: `MessageStatus` union (T2) consumed by T3/T4; `updateMessage` signature identical in T2 (defined) and T3/T4 (consumed); `DeliveryStatus.FAILED` (T6) consumed by T8/T9; `readByMessage` StateFlow (T7) consumed by T8; `commClient.send(channelId, content, opts)` shape shared by T2/T3/T4; backend `media` shape (T1) matches portal `MediaAttachment` field-for-field.
- Placeholders: none — every code step carries real code or an exact contract with file:line anchors from the two scout reports; UI-only Android tasks (T8-T10) name exact composables, lines, tokens, and grep gates.
- Known risks named in-plan: combinedClickable/draggable gesture coexistence (T9 reports on it), Maestro-pinned strings (T10 greps first), swipe-container intrinsic height (T8 gives the matchParentSize pattern).
