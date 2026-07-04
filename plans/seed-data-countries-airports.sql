-- Seed data: real countries and their major airports, for manual import into the
-- dev Postgres database (docker-compose `postgres` service, db/user/password:
-- `aviation`). Matches the CURRENT schema shape (V1/V2/V6 — natural-key PKs,
-- `airports.country_code` FK) as of this writing.
--
-- If `V7__add_surrogate_keys.sql` (see plans/surrogate-long-keys-country-airport.md)
-- lands first, this file's INSERTs need adapting: `airports.country_code` becomes
-- `airports.country_id`, requiring a `(SELECT id FROM countries WHERE code = ...)`
-- per row, or a temporary join/CTE — not adapted here since that migration hasn't
-- been applied yet.
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
INSERT INTO airports (iata_code, icao_code, name, city, country_code) VALUES
  ('MAD', 'LEMD', 'Adolfo Suárez Madrid–Barajas Airport', 'Madrid', 'ES'),
  ('BCN', 'LEBL', 'Josep Tarradellas Barcelona-El Prat Airport', 'Barcelona', 'ES'),
  ('AGP', 'LEMG', 'Málaga-Costa del Sol Airport', 'Málaga', 'ES'),
  ('PMI', 'LEPA', 'Palma de Mallorca Airport', 'Palma de Mallorca', 'ES'),
  ('VLC', 'LEVC', 'Valencia Airport', 'Valencia', 'ES')
ON CONFLICT (iata_code) DO NOTHING;

-- ============================================================
-- Airports — United Kingdom (GB)
-- ============================================================
INSERT INTO airports (iata_code, icao_code, name, city, country_code) VALUES
  ('LHR', 'EGLL', 'London Heathrow Airport', 'London', 'GB'),
  ('LGW', 'EGKK', 'London Gatwick Airport', 'London', 'GB'),
  ('MAN', 'EGCC', 'Manchester Airport', 'Manchester', 'GB'),
  ('STN', 'EGSS', 'London Stansted Airport', 'London', 'GB'),
  ('EDI', 'EGPH', 'Edinburgh Airport', 'Edinburgh', 'GB')
ON CONFLICT (iata_code) DO NOTHING;

-- ============================================================
-- Airports — France (FR)
-- ============================================================
INSERT INTO airports (iata_code, icao_code, name, city, country_code) VALUES
  ('CDG', 'LFPG', 'Paris Charles de Gaulle Airport', 'Paris', 'FR'),
  ('ORY', 'LFPO', 'Paris Orly Airport', 'Paris', 'FR'),
  ('NCE', 'LFMN', 'Nice Côte d''Azur Airport', 'Nice', 'FR'),
  ('LYS', 'LFLL', 'Lyon–Saint-Exupéry Airport', 'Lyon', 'FR'),
  ('MRS', 'LFML', 'Marseille Provence Airport', 'Marseille', 'FR')
ON CONFLICT (iata_code) DO NOTHING;

-- ============================================================
-- Airports — Germany (DE)
-- ============================================================
INSERT INTO airports (iata_code, icao_code, name, city, country_code) VALUES
  ('FRA', 'EDDF', 'Frankfurt Airport', 'Frankfurt', 'DE'),
  ('MUC', 'EDDM', 'Munich Airport', 'Munich', 'DE'),
  ('BER', 'EDDB', 'Berlin Brandenburg Airport', 'Berlin', 'DE'),
  ('DUS', 'EDDL', 'Düsseldorf Airport', 'Düsseldorf', 'DE'),
  ('HAM', 'EDDH', 'Hamburg Airport', 'Hamburg', 'DE')
ON CONFLICT (iata_code) DO NOTHING;
