-- Sub-project 5: pre-minted founder-pricing scarcity pools (F5.1). One row per
-- seat; reserve() grabs one under FOR UPDATE SKIP LOCKED. held_by/claimed_by ->
-- users(id) (paired with profiles; consistent with the trials table).
CREATE TABLE IF NOT EXISTS founder_seats (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bonus_id    TEXT NOT NULL CHECK (bonus_id IN (
                'solo_founder_pricing_lock',
                'advanced_lifetime_template_library',
                'enterprise_founder_annual_pricing')),
  seat_number INTEGER NOT NULL,
  total_seats INTEGER NOT NULL,
  -- 'released' is reserved for a future explicit cancel/release flow (billing);
  -- expiry returns a hold to 'available', not 'released'.
  status      TEXT NOT NULL DEFAULT 'available'
                CHECK (status IN ('available', 'held', 'claimed', 'released')),
  held_by     TEXT REFERENCES users(id),
  held_until  TIMESTAMPTZ,
  claimed_by  TEXT REFERENCES users(id),
  claimed_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (bonus_id, seat_number)
);

CREATE INDEX IF NOT EXISTS idx_founder_seats_status ON founder_seats (bonus_id, status);
CREATE INDEX IF NOT EXISTS idx_founder_seats_held_until
  ON founder_seats (held_until) WHERE status = 'held';

-- Pre-mint each pool once (idempotent: skip if the pool already exists).
INSERT INTO founder_seats (bonus_id, seat_number, total_seats)
SELECT 'solo_founder_pricing_lock', g, 1000 FROM generate_series(1, 1000) AS g
WHERE NOT EXISTS (SELECT 1 FROM founder_seats WHERE bonus_id = 'solo_founder_pricing_lock');

INSERT INTO founder_seats (bonus_id, seat_number, total_seats)
SELECT 'advanced_lifetime_template_library', g, 100 FROM generate_series(1, 100) AS g
WHERE NOT EXISTS (SELECT 1 FROM founder_seats WHERE bonus_id = 'advanced_lifetime_template_library');

INSERT INTO founder_seats (bonus_id, seat_number, total_seats)
SELECT 'enterprise_founder_annual_pricing', g, 10 FROM generate_series(1, 10) AS g
WHERE NOT EXISTS (SELECT 1 FROM founder_seats WHERE bonus_id = 'enterprise_founder_annual_pricing');
