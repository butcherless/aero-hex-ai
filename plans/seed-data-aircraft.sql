-- Seed data: real aircraft for each seeded airline, for manual import into the
-- dev Postgres database (docker-compose `postgres` service, db/user/password:
-- `aviation`). Matches the CURRENT schema shape (V11: `aircraft.airline_id
-- BIGINT` FK -> `airlines.id`, `registration` UNIQUE NOT NULL, `type_code`/
-- `description VARCHAR NOT NULL`). Aircraft therefore insert via a
-- VALUES/JOIN on the airline ICAO code so each row resolves its surrogate
-- `airline_id`, same pattern as seed-data-airlines.sql. Requires airlines
-- IBE/VLG/BAW/EZY/AFR/TVF/DLH/EWG to already exist (see that file).
--
-- Idempotent: ON CONFLICT (registration) DO NOTHING, safe to re-run.
--
-- Registrations, ICAO type designators, and descriptions are real,
-- currently-registered aircraft for each airline (source: Planespotters.net,
-- Airfleets.net, Flightradar24, airline fleet fact sheets — see chat for the
-- exact search queries/sources used).

-- ============================================================
-- Aircraft — Iberia (IBE)
-- ============================================================
INSERT INTO aircraft (registration, type_code, description, airline_id)
SELECT v.registration, v.type_code, v.description, l.id
FROM (VALUES
  ('EC-NGT', 'A359', 'Airbus A350-900', 'IBE'),
  ('EC-OAX', 'A359', 'Airbus A350-900', 'IBE')
) AS v(registration, type_code, description, airline_icao)
JOIN airlines l ON l.icao_code = v.airline_icao
ON CONFLICT (registration) DO NOTHING;

-- ============================================================
-- Aircraft — Vueling (VLG)
-- ============================================================
INSERT INTO aircraft (registration, type_code, description, airline_id)
SELECT v.registration, v.type_code, v.description, l.id
FROM (VALUES
  ('EC-MKO', 'A320', 'Airbus A320-200', 'VLG'),
  ('EC-LVU', 'A320', 'Airbus A320-200', 'VLG')
) AS v(registration, type_code, description, airline_icao)
JOIN airlines l ON l.icao_code = v.airline_icao
ON CONFLICT (registration) DO NOTHING;

-- ============================================================
-- Aircraft — British Airways (BAW)
-- ============================================================
INSERT INTO aircraft (registration, type_code, description, airline_id)
SELECT v.registration, v.type_code, v.description, l.id
FROM (VALUES
  ('G-XWBL', 'A35K', 'Airbus A350-1000', 'BAW'),
  ('G-XWBP', 'A35K', 'Airbus A350-1000', 'BAW')
) AS v(registration, type_code, description, airline_icao)
JOIN airlines l ON l.icao_code = v.airline_icao
ON CONFLICT (registration) DO NOTHING;

-- ============================================================
-- Aircraft — easyJet (EZY)
-- ============================================================
INSERT INTO aircraft (registration, type_code, description, airline_id)
SELECT v.registration, v.type_code, v.description, l.id
FROM (VALUES
  ('G-UZLO', 'A20N', 'Airbus A320neo', 'EZY'),
  ('G-UJEA', 'A20N', 'Airbus A320neo', 'EZY')
) AS v(registration, type_code, description, airline_icao)
JOIN airlines l ON l.icao_code = v.airline_icao
ON CONFLICT (registration) DO NOTHING;

-- ============================================================
-- Aircraft — Air France (AFR)
-- ============================================================
INSERT INTO aircraft (registration, type_code, description, airline_id)
SELECT v.registration, v.type_code, v.description, l.id
FROM (VALUES
  ('F-HTYA', 'A359', 'Airbus A350-900', 'AFR'),
  ('F-HTYT', 'A359', 'Airbus A350-900', 'AFR')
) AS v(registration, type_code, description, airline_icao)
JOIN airlines l ON l.icao_code = v.airline_icao
ON CONFLICT (registration) DO NOTHING;

-- ============================================================
-- Aircraft — Transavia France (TVF)
-- ============================================================
INSERT INTO aircraft (registration, type_code, description, airline_id)
SELECT v.registration, v.type_code, v.description, l.id
FROM (VALUES
  ('F-HTVS', 'B738', 'Boeing 737-800', 'TVF'),
  ('F-HTVJ', 'B738', 'Boeing 737-800', 'TVF')
) AS v(registration, type_code, description, airline_icao)
JOIN airlines l ON l.icao_code = v.airline_icao
ON CONFLICT (registration) DO NOTHING;

-- ============================================================
-- Aircraft — Lufthansa (DLH)
-- ============================================================
INSERT INTO aircraft (registration, type_code, description, airline_id)
SELECT v.registration, v.type_code, v.description, l.id
FROM (VALUES
  ('D-AIXX', 'A359', 'Airbus A350-900', 'DLH'),
  ('D-AIVE', 'A359', 'Airbus A350-900', 'DLH')
) AS v(registration, type_code, description, airline_icao)
JOIN airlines l ON l.icao_code = v.airline_icao
ON CONFLICT (registration) DO NOTHING;

-- ============================================================
-- Aircraft — Eurowings (EWG)
-- ============================================================
INSERT INTO aircraft (registration, type_code, description, airline_id)
SELECT v.registration, v.type_code, v.description, l.id
FROM (VALUES
  ('D-ABNL', 'A320', 'Airbus A320-200', 'EWG'),
  ('D-ABZI', 'A320', 'Airbus A320-200', 'EWG')
) AS v(registration, type_code, description, airline_icao)
JOIN airlines l ON l.icao_code = v.airline_icao
ON CONFLICT (registration) DO NOTHING;
