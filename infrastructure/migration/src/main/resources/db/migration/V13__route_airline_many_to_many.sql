-- Route<->Airline is many-to-many (a route is flown by several airlines; an airline operates
-- several routes), not the one-airline-per-route-row design V4/V7 encoded via routes.airline_id
-- + a 3-column UNIQUE(origin, destination, airline). Route becomes airline-agnostic
-- infrastructure; the association moves to its own join table. See
-- plans/dapper-swimming-micali.md (Route entity review) for the full rationale.

-- Phase 1: drop the old FK, its index, and the 3-column unique constraint tied to it
ALTER TABLE routes DROP CONSTRAINT routes_airline_id_fkey;
DROP INDEX idx_routes_airline_id;
ALTER TABLE routes DROP CONSTRAINT uq_route_segment;

-- Phase 2: drop the now-unused column
ALTER TABLE routes DROP COLUMN airline_id;

-- Phase 3: a route segment (origin, destination) is now unique on its own
ALTER TABLE routes ADD CONSTRAINT uq_route_segment UNIQUE (origin_airport_id, destination_airport_id);

-- Phase 4: routes.id is no longer domain-generated (RouteId.generate is gone) — the DB
-- generates it now. PG16 has gen_random_uuid() built in, no extension required.
ALTER TABLE routes ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Phase 5: the join table itself
CREATE TABLE route_airlines (
    route_id   UUID   NOT NULL REFERENCES routes (id),
    airline_id BIGINT NOT NULL REFERENCES airlines (id),
    PRIMARY KEY (route_id, airline_id)
);

CREATE INDEX idx_route_airlines_airline_id ON route_airlines (airline_id);
