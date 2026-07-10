-- Seed data: real airlines for each seeded country, for manual import into the
-- dev Postgres database (docker-compose `postgres` service, db/user/password:
-- `aviation`). Matches the CURRENT schema shape (post-V10: `airlines.country_id
-- BIGINT` FK -> `countries.id`, `foundation_date DATE NOT NULL` added in V9).
-- Airlines therefore insert via a VALUES/JOIN on the country code so each row
-- resolves its surrogate `country_id`, same pattern as
-- seed-data-countries-airports.sql. Requires countries ES/GB/FR/DE to already
-- exist (see that file).
--
-- Idempotent: ON CONFLICT (icao_code) DO NOTHING, safe to re-run.
--
-- Airline ICAO codes and foundation dates are the well-established public
-- facts for each airline (source: ICAO airline registry / airline history).
-- Some foundation dates are approximate to the year where the exact
-- incorporation date isn't a commonly cited fact (Vueling, Transavia France,
-- Eurowings) -- same precision level as the airport seed data.

-- ============================================================
-- Airlines — Spain (ES)
-- ============================================================
INSERT INTO airlines (icao_code, name, foundation_date, country_id)
SELECT v.icao, v.name, v.foundation_date, c.id
FROM (VALUES
  ('IBE', 'Iberia', DATE '1927-06-28', 'ES'),
  ('VLG', 'Vueling', DATE '2004-03-01', 'ES')
) AS v(icao, name, foundation_date, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (icao_code) DO NOTHING;

-- ============================================================
-- Airlines — United Kingdom (GB)
-- ============================================================
INSERT INTO airlines (icao_code, name, foundation_date, country_id)
SELECT v.icao, v.name, v.foundation_date, c.id
FROM (VALUES
  ('BAW', 'British Airways', DATE '1974-03-31', 'GB'),
  ('EZY', 'easyJet', DATE '1995-03-10', 'GB')
) AS v(icao, name, foundation_date, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (icao_code) DO NOTHING;

-- ============================================================
-- Airlines — France (FR)
-- ============================================================
INSERT INTO airlines (icao_code, name, foundation_date, country_id)
SELECT v.icao, v.name, v.foundation_date, c.id
FROM (VALUES
  ('AFR', 'Air France', DATE '1933-10-07', 'FR'),
  ('TVF', 'Transavia France', DATE '2007-01-01', 'FR')
) AS v(icao, name, foundation_date, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (icao_code) DO NOTHING;

-- ============================================================
-- Airlines — Germany (DE)
-- ============================================================
INSERT INTO airlines (icao_code, name, foundation_date, country_id)
SELECT v.icao, v.name, v.foundation_date, c.id
FROM (VALUES
  ('DLH', 'Lufthansa', DATE '1953-01-06', 'DE'),
  ('EWG', 'Eurowings', DATE '1993-01-01', 'DE')
) AS v(icao, name, foundation_date, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (icao_code) DO NOTHING;
