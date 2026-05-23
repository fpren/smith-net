-- Sub-project 6a: the real subscriptions table (permanent tier source). Provider
-- adapters (6b) normalize webhooks into applySubscriptionEvent. user_id ->
-- users(id) (consistent with prior tier sub-projects). UNIQUE(provider,
-- provider_subscription_id) makes the upsert idempotent for replayed events.
CREATE TABLE IF NOT EXISTS subscriptions (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                  TEXT NOT NULL REFERENCES users(id),
  tier                     TEXT NOT NULL CHECK (tier IN ('solo', 'advanced', 'enterprise')),
  cadence                  TEXT NOT NULL DEFAULT 'monthly' CHECK (cadence IN ('monthly', 'annual')),
  provider                 TEXT NOT NULL CHECK (provider IN ('stripe', 'play_billing', 'manual')),
  provider_subscription_id TEXT NOT NULL,
  status                   TEXT NOT NULL CHECK (status IN ('trialing', 'active', 'past_due', 'canceled', 'expired')),
  current_period_start     TIMESTAMPTZ,
  current_period_end       TIMESTAMPTZ,
  cancel_at_period_end     BOOLEAN NOT NULL DEFAULT false,
  founder_seat_id          UUID REFERENCES founder_seats(id),
  founder_price_locked     BOOLEAN NOT NULL DEFAULT false,
  cents_per_period         INTEGER NOT NULL DEFAULT 0,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_subscription_id)
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user ON subscriptions (user_id, status);
