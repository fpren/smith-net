CREATE TABLE IF NOT EXISTS wage_data (
    id SERIAL PRIMARY KEY,
    metro_area_code TEXT NOT NULL,
    metro_area_name TEXT NOT NULL,
    soc_code TEXT NOT NULL,
    occupation_title TEXT NOT NULL,
    median_hourly DECIMAL,
    mean_hourly DECIMAL,
    p25_hourly DECIMAL,
    p75_hourly DECIMAL,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(metro_area_code, soc_code)
);
CREATE TABLE IF NOT EXISTS zip_metro_map (
    zip_code TEXT PRIMARY KEY,
    cbsa_code TEXT,
    metro_name TEXT
);
CREATE INDEX idx_wage_data_soc ON wage_data(soc_code);
CREATE INDEX idx_zip_metro ON zip_metro_map(zip_code);
