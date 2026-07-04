-- Introduce a uniform Long/BIGINT surrogate primary key on countries, airports,
-- and airlines, and redirect every FK in the schema to reference surrogate ids
-- instead of natural business keys. Natural keys (code, iata_code, icao_code)
-- remain as UNIQUE NOT NULL columns and keep serving business lookups.
--
-- routes.id and outbox_events.id stay UUID: neither is a natural business key,
-- and nothing FKs to either, so they aren't required to change to satisfy the
-- invariant. See plans/surrogate-long-keys-country-airport.md for the full
-- design rationale.
--
-- Ordering is load-bearing: Postgres refuses to drop a constraint while another
-- object still depends on it, so every old FK must be dropped before the PK it
-- depends on, and every new FK must be added after its target's new PK exists.

-- Phase 0: extension for trigram search indexes
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Phase 1: add surrogate id columns (additive, harmless)
ALTER TABLE countries ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY;
ALTER TABLE airports  ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY;
ALTER TABLE airlines  ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY;

-- Phase 2: add new FK columns (nullable for now)
ALTER TABLE airports ADD COLUMN country_id BIGINT;
ALTER TABLE airlines ADD COLUMN country_id BIGINT;
ALTER TABLE routes   ADD COLUMN origin_airport_id      BIGINT;
ALTER TABLE routes   ADD COLUMN destination_airport_id BIGINT;
ALTER TABLE routes   ADD COLUMN airline_id             BIGINT;

-- Phase 3: backfill new FK columns from the natural-key relationships
UPDATE airports a SET country_id = c.id FROM countries c WHERE a.country_code = c.code;
UPDATE airlines l SET country_id = c.id FROM countries c WHERE l.country_code = c.code;
UPDATE routes r SET origin_airport_id = ao.id
  FROM airports ao WHERE r.origin_iata = ao.iata_code;
UPDATE routes r SET destination_airport_id = ad.id
  FROM airports ad WHERE r.destination_iata = ad.iata_code;
UPDATE routes r SET airline_id = al.id
  FROM airlines al WHERE r.airline_icao = al.icao_code;

-- Phase 4: enforce NOT NULL now that every row is backfilled
ALTER TABLE airports ALTER COLUMN country_id SET NOT NULL;
ALTER TABLE airlines ALTER COLUMN country_id SET NOT NULL;
ALTER TABLE routes ALTER COLUMN origin_airport_id      SET NOT NULL;
ALTER TABLE routes ALTER COLUMN destination_airport_id SET NOT NULL;
ALTER TABLE routes ALTER COLUMN airline_id             SET NOT NULL;

-- Phase 5: drop old FKs, old indexes on those FKs, and the old UNIQUE tied to them
ALTER TABLE airports DROP CONSTRAINT airports_country_code_fkey;
ALTER TABLE airlines DROP CONSTRAINT airlines_country_code_fkey;
ALTER TABLE routes DROP CONSTRAINT routes_origin_iata_fkey;
ALTER TABLE routes DROP CONSTRAINT routes_destination_iata_fkey;
ALTER TABLE routes DROP CONSTRAINT routes_airline_icao_fkey;
ALTER TABLE routes DROP CONSTRAINT uq_route_segment;

DROP INDEX idx_airports_country;
DROP INDEX idx_airlines_country;
DROP INDEX idx_routes_origin;
DROP INDEX idx_routes_destination;
DROP INDEX idx_routes_airline;

-- Phase 6: drop the old natural-key FK columns (no longer referenced by anything)
ALTER TABLE airports DROP COLUMN country_code;
ALTER TABLE airlines DROP COLUMN country_code;
ALTER TABLE routes DROP COLUMN origin_iata;
ALTER TABLE routes DROP COLUMN destination_iata;
ALTER TABLE routes DROP COLUMN airline_icao;

-- Phase 7: swap primary keys now that nothing depends on the old ones
ALTER TABLE countries DROP CONSTRAINT countries_pkey;
ALTER TABLE countries ADD PRIMARY KEY (id);
ALTER TABLE countries ADD CONSTRAINT countries_code_key UNIQUE (code);

ALTER TABLE airports DROP CONSTRAINT airports_pkey;
ALTER TABLE airports ADD PRIMARY KEY (id);
ALTER TABLE airports ADD CONSTRAINT airports_iata_code_key UNIQUE (iata_code);

ALTER TABLE airlines DROP CONSTRAINT airlines_pkey;
ALTER TABLE airlines ADD PRIMARY KEY (id);
ALTER TABLE airlines ADD CONSTRAINT airlines_icao_code_key UNIQUE (icao_code);

-- Phase 8: add the new surrogate-id FKs
ALTER TABLE airports ADD CONSTRAINT airports_country_id_fkey
  FOREIGN KEY (country_id) REFERENCES countries (id);
ALTER TABLE airlines ADD CONSTRAINT airlines_country_id_fkey
  FOREIGN KEY (country_id) REFERENCES countries (id);
ALTER TABLE routes ADD CONSTRAINT routes_origin_airport_id_fkey
  FOREIGN KEY (origin_airport_id) REFERENCES airports (id);
ALTER TABLE routes ADD CONSTRAINT routes_destination_airport_id_fkey
  FOREIGN KEY (destination_airport_id) REFERENCES airports (id);
ALTER TABLE routes ADD CONSTRAINT routes_airline_id_fkey
  FOREIGN KEY (airline_id) REFERENCES airlines (id);
ALTER TABLE routes ADD CONSTRAINT uq_route_segment
  UNIQUE (origin_airport_id, destination_airport_id, airline_id);

-- Phase 9: new indexes - FK equality lookups + business-search trigram indexes
CREATE INDEX idx_airports_country_id           ON airports (country_id);
CREATE INDEX idx_airlines_country_id           ON airlines (country_id);
CREATE INDEX idx_routes_origin_airport_id      ON routes (origin_airport_id);
CREATE INDEX idx_routes_destination_airport_id ON routes (destination_airport_id);
CREATE INDEX idx_routes_airline_id             ON routes (airline_id);
CREATE INDEX idx_countries_name_trgm ON countries USING GIN (name gin_trgm_ops);
CREATE INDEX idx_airports_name_trgm  ON airports  USING GIN (name gin_trgm_ops);
