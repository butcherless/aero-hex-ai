-- Seed data: real airlines for each seeded country, for manual import into the
-- dev Postgres database (docker-compose `postgres` service, db/user/password:
-- `aviation`). Matches the CURRENT schema shape (post-V15: `airlines.country_id
-- BIGINT` FK -> `countries.id`, `alias`/`callsign VARCHAR(100)` nullable, added
-- in V15 replacing the removed `foundation_date` column). Airlines therefore
-- insert via a VALUES/JOIN on the country code so each row resolves its
-- surrogate `country_id`, same pattern as seed-data-countries-airports.sql.
-- Requires countries ES/GB/FR/DE to already exist (see that file).
--
-- Idempotent: ON CONFLICT (icao_code) DO NOTHING, safe to re-run.
--
-- Callsigns are the well-established public facts for each airline (source:
-- ICAO airline registry). Alias is left NULL where the airline has no
-- alternative commercial name distinct from its registered name.

-- ============================================================
-- Airlines — Spain (ES)
-- ============================================================
INSERT INTO airlines (icao_code, name, alias, callsign, country_id)
SELECT v.icao, v.name, v.alias, v.callsign, c.id
FROM (VALUES
  ('IBE', 'Iberia', NULL, 'IBERIA', 'ES'),
  ('VLG', 'Vueling', NULL, 'VUELING', 'ES')
) AS v(icao, name, alias, callsign, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (icao_code) DO NOTHING;

-- ============================================================
-- Airlines — United Kingdom (GB)
-- ============================================================
INSERT INTO airlines (icao_code, name, alias, callsign, country_id)
SELECT v.icao, v.name, v.alias, v.callsign, c.id
FROM (VALUES
  ('BAW', 'British Airways', NULL, 'SPEEDBIRD', 'GB'),
  ('EZY', 'easyJet', NULL, 'EASY', 'GB')
) AS v(icao, name, alias, callsign, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (icao_code) DO NOTHING;

-- ============================================================
-- Airlines — France (FR)
-- ============================================================
INSERT INTO airlines (icao_code, name, alias, callsign, country_id)
SELECT v.icao, v.name, v.alias, v.callsign, c.id
FROM (VALUES
  ('AFR', 'Air France', NULL, 'AIRFRANS', 'FR'),
  ('TVF', 'Transavia France', NULL, 'TRANSAVIA FRANCE', 'FR')
) AS v(icao, name, alias, callsign, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (icao_code) DO NOTHING;

-- ============================================================
-- Airlines — Germany (DE)
-- ============================================================
INSERT INTO airlines (icao_code, name, alias, callsign, country_id)
SELECT v.icao, v.name, v.alias, v.callsign, c.id
FROM (VALUES
  ('DLH', 'Lufthansa', NULL, 'LUFTHANSA', 'DE'),
  ('EWG', 'Eurowings', NULL, 'EUROWINGS', 'DE')
) AS v(icao, name, alias, callsign, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (icao_code) DO NOTHING;
