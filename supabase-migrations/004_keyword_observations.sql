-- keyword_observations.sql
-- Stores keywords detected during TEST ⧉ Playwright runs for analysis
-- APPEND-ONLY: No updates, no deletes in normal operation

CREATE TABLE IF NOT EXISTS keyword_observations (
  id BIGSERIAL PRIMARY KEY,
  keyword TEXT NOT NULL,
  trade_guess TEXT,
  source_mode TEXT NOT NULL DEFAULT 'test_button_playwright',
  detected_in TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for filtering by source
CREATE INDEX IF NOT EXISTS idx_keyword_obs_source 
  ON keyword_observations(source_mode);

-- Index for filtering by trade
CREATE INDEX IF NOT EXISTS idx_keyword_obs_trade 
  ON keyword_observations(trade_guess);

-- Index for time-based queries
CREATE INDEX IF NOT EXISTS idx_keyword_obs_created 
  ON keyword_observations(created_at DESC);

-- Comments for documentation
COMMENT ON TABLE keyword_observations IS 'Append-only log of keywords detected during TEST ⧉ Playwright runs';
COMMENT ON COLUMN keyword_observations.keyword IS 'The detected keyword';
COMMENT ON COLUMN keyword_observations.trade_guess IS 'Guessed trade category (e.g., ELECTRICAL, PLUMBING)';
COMMENT ON COLUMN keyword_observations.source_mode IS 'Source of detection: test_button_playwright, planner_test_compile';
COMMENT ON COLUMN keyword_observations.detected_in IS 'Original input text where keyword was detected';
COMMENT ON COLUMN keyword_observations.created_at IS 'Timestamp of observation';
