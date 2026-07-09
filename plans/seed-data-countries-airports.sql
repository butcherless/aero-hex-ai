-- Seed data: real countries and their major airports, for manual import into the
-- dev Postgres database (docker-compose `postgres` service, db/user/password:
-- `aviation`). Matches the CURRENT schema shape (post-V7 surrogate keys:
-- `airports.country_id BIGINT` FK → `countries.id`; natural keys are UNIQUE
-- lookup columns). Airports therefore insert via a VALUES/JOIN on the country
-- code so each row resolves its surrogate `country_id`.
--
-- Idempotent: ON CONFLICT ... DO NOTHING on the natural keys, safe to re-run.
--
-- Country codes: ISO 3166-1 alpha-2. Airport IATA/ICAO codes and city are the
-- well-established public codes for each airport (source: IATA/ICAO airport
-- registries).

-- ============================================================
-- Countries
-- ============================================================
INSERT INTO countries (code, name) VALUES
  ('ES', 'Spain'),
  ('GB', 'United Kingdom'),
  ('FR', 'France'),
  ('DE', 'Germany')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- Airports — Spain (ES)
-- ============================================================
INSERT INTO airports (iata_code, icao_code, name, city, country_id)
SELECT v.iata, v.icao, v.name, v.city, c.id
FROM (VALUES
  ('MAD', 'LEMD', 'Adolfo Suárez Madrid–Barajas Airport', 'Madrid', 'ES'),
  ('BCN', 'LEBL', 'Josep Tarradellas Barcelona-El Prat Airport', 'Barcelona', 'ES'),
  ('AGP', 'LEMG', 'Málaga-Costa del Sol Airport', 'Málaga', 'ES'),
  ('PMI', 'LEPA', 'Palma de Mallorca Airport', 'Palma de Mallorca', 'ES'),
  ('VLC', 'LEVC', 'Valencia Airport', 'Valencia', 'ES')
) AS v(iata, icao, name, city, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (iata_code) DO NOTHING;

-- ============================================================
-- Airports — United Kingdom (GB)
-- ============================================================
INSERT INTO airports (iata_code, icao_code, name, city, country_id)
SELECT v.iata, v.icao, v.name, v.city, c.id
FROM (VALUES
  ('LHR', 'EGLL', 'London Heathrow Airport', 'London', 'GB'),
  ('LGW', 'EGKK', 'London Gatwick Airport', 'London', 'GB'),
  ('MAN', 'EGCC', 'Manchester Airport', 'Manchester', 'GB'),
  ('STN', 'EGSS', 'London Stansted Airport', 'London', 'GB'),
  ('EDI', 'EGPH', 'Edinburgh Airport', 'Edinburgh', 'GB')
) AS v(iata, icao, name, city, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (iata_code) DO NOTHING;

-- ============================================================
-- Airports — France (FR)
-- ============================================================
INSERT INTO airports (iata_code, icao_code, name, city, country_id)
SELECT v.iata, v.icao, v.name, v.city, c.id
FROM (VALUES
  ('CDG', 'LFPG', 'Paris Charles de Gaulle Airport', 'Paris', 'FR'),
  ('ORY', 'LFPO', 'Paris Orly Airport', 'Paris', 'FR'),
  ('NCE', 'LFMN', 'Nice Côte d''Azur Airport', 'Nice', 'FR'),
  ('LYS', 'LFLL', 'Lyon–Saint-Exupéry Airport', 'Lyon', 'FR'),
  ('MRS', 'LFML', 'Marseille Provence Airport', 'Marseille', 'FR')
) AS v(iata, icao, name, city, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (iata_code) DO NOTHING;

-- ============================================================
-- Airports — Germany (DE)
-- ============================================================
INSERT INTO airports (iata_code, icao_code, name, city, country_id)
SELECT v.iata, v.icao, v.name, v.city, c.id
FROM (VALUES
  ('FRA', 'EDDF', 'Frankfurt Airport', 'Frankfurt', 'DE'),
  ('MUC', 'EDDM', 'Munich Airport', 'Munich', 'DE'),
  ('BER', 'EDDB', 'Berlin Brandenburg Airport', 'Berlin', 'DE'),
  ('DUS', 'EDDL', 'Düsseldorf Airport', 'Düsseldorf', 'DE'),
  ('HAM', 'EDDH', 'Hamburg Airport', 'Hamburg', 'DE')
) AS v(iata, icao, name, city, country_code)
JOIN countries c ON c.code = v.country_code
ON CONFLICT (iata_code) DO NOTHING;
