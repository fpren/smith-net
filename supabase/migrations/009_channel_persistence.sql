-- 009_channel_persistence.sql
-- Adds per-channel persistence mode.
--
-- PERSISTENT (default, existing behavior) — messages are stored in the
-- messages table and late joiners reconcile the history.
--
-- EPHEMERAL — messages are fanned out via Supabase Realtime broadcast only.
-- No row is written to messages. Clients that weren't subscribed at send
-- time will never see the message. This mirrors Bitchat's mesh semantics
-- applied to the online tier.

alter table public.channels
  add column if not exists persistence text not null default 'persistent'
  check (persistence in ('persistent', 'ephemeral'));

comment on column public.channels.persistence is
  'persistent: messages stored in messages table; ephemeral: Realtime broadcast only, no cloud copy';
