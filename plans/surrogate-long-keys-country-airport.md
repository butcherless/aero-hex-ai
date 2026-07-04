# Plan: surrogate `Long` PKs across the schema (Country, Airport, Airline, Route FKs)

## Goal

Relate `Country` and `Airport` with a standard many-to-one (Airport → Country). Every
table gets a `Long` surrogate primary key whose sole purpose is to make the storage
model uniform, simple, and performant — a single, consistent identity/join
mechanism across all tables, regardless of what shape a table's business key
happens to be. It is not meant to serve business lookups. Natural (business) keys —
`code`, `iata_code`, `icao_code`, `name` — remain the vocabulary of the business
finders (`findByCode`/`findByIata`, `searchByName` ILIKE, `findByCountry`) and get
their own indexes accordingly. No collections on either domain entity (Country does
not gain a `List[Airport]` — this already holds today).

**Scope grew from the original Country/Airport-only ask.** Review of the full
migration set (V1–V6) found that `airlines.country_code REFERENCES countries(code)`
and `routes.origin_iata`/`routes.destination_iata REFERENCES airports(iata_code)`
already depend on the exact columns this plan swaps the primary key of. Postgres
refuses to drop a PK/unique constraint while any FK still depends on it, so making
`countries.id`/`airports.id` the literal primary key is not possible without also
migrating `airlines` and `routes` off their natural-key FKs in the same migration —
confirmed with the user, who chose the full cutover over a hybrid/incremental
approach. Every table's FK columns are redirected to surrogate ids in this one
migration; nothing is left half-migrated.

**Explicitly out of scope:** `routes.id` and `outbox_events.id` are already `UUID`,
not natural business keys, and nothing FKs to either of them — so neither is
required to change to satisfy the "every FK is a surrogate id" invariant. They are
left as `UUID` here. Flagging this as a conscious boundary, not an oversight — say so
if full `Long`-uniformity across every table's own PK (not just FK targets) is
wanted; that would be a separate, larger plan.

## Current state (baseline)

- `countries`: PK = `code VARCHAR(2)`. `Country` domain model has no numeric id;
  identity is `CountryCode`.
- `airports`: PK = `iata_code VARCHAR(3)`, FK
  `country_code VARCHAR(2) REFERENCES countries(code)`, index
  `idx_airports_country (country_code)`.
- `airlines`: PK = `icao_code VARCHAR(3)`, FK
  `country_code VARCHAR(2) REFERENCES countries(code)`, index
  `idx_airlines_country (country_code)`.
- `routes`: PK = `id UUID` (already a surrogate, untouched by this plan), FKs
  `origin_iata`/`destination_iata REFERENCES airports(iata_code)`,
  `airline_icao REFERENCES airlines(icao_code)`, plus
  `UNIQUE (origin_iata, destination_iata, airline_icao)` and three indexes
  (`idx_routes_origin`, `idx_routes_destination`, `idx_routes_airline`).
- `outbox_events`: PK = `id UUID`, no FKs to/from `countries`/`airports`/`airlines`/
  `routes` — entirely unaffected by this plan.
- Wired into `bootstrap` today: `QuillCountryRepository` (Country),
  `DoobieAirportRepository` (Airport). `DoobieCountryRepository`,
  `DoobieAirlineRepository`, `DoobieRouteRepository` exist but are **unwired dead
  code** (per `CLAUDE.md`) — still touched by this plan for schema consistency, but
  carry zero runtime risk since nothing exercises them.
- Pre-existing, unrelated bug noticed in passing: `DoobieAirlineRepository`
  reads/writes a `foundation_date` column that was never added by any migration —
  this repository would fail at runtime if ever wired. Not touched by this plan;
  noted so it isn't mistaken for something this change introduced.
- No repository-level (Quill/Doobie) test suites exist anywhere — `AirportEndpointsSpec`/
  `CountryEndpointsSpec` stub the use-case traits directly and never invoke real SQL.
  This means the manual end-to-end verification step later in this plan is the
  **only** safety net for every query rewritten here — there is no automated
  regression coverage to catch a mistake.

## Design decisions

### Decision 1 — the `Long` id lives only in the persistence layer (confirmed)

Confined to the **DB schema and the private Quill/Doobie row types**; the domain
model (`Country`, `Airport`, `Airline`, `Route`, `CountryCode`, `IataCode`,
`IcaoCode`, `RouteId`) is untouched. Zero ripple into `port/in`, `port/out`,
`application/`, `adapter-http/`, or `bootstrap/` — every repository port signature
stays keyed on its business-code type exactly as today.

**Type mapping — `Long` ↔ `BIGINT`, no custom codec.** Scala `Long` in
row/repository code, `BIGINT GENERATED ALWAYS AS IDENTITY` in Postgres — the
default mapping both Doobie (`Meta[Long]`) and Quill support with zero custom
`Meta`/encoder. No `UUID`, no `SERIAL`/`INTEGER`, no string-typed id for these four
tables' primary keys. Strictly an infra/storage detail: generated, read, and
discarded entirely within the persistence modules, never appearing in a port
signature, application service, DTO, or domain case class.

### Decision 2 — every FK across the schema references a surrogate id (full cutover)

**Invariant:** every FK column is `BIGINT REFERENCES <parent>(id)` — one
relationship mechanism, one FK type, uniform regardless of the parent's business-key
shape. Natural keys stay reserved for lookups (`WHERE code = ?`), never for joins.
Concretely, this migration redirects:

| Child column (today) | → | New column | New FK target |
|---|---|---|---|
| `airports.country_code` | → | `airports.country_id` | `countries(id)` |
| `airlines.country_code` | → | `airlines.country_id` | `countries(id)` |
| `routes.origin_iata` | → | `routes.origin_airport_id` | `airports(id)` |
| `routes.destination_iata` | → | `routes.destination_airport_id` | `airports(id)` |
| `routes.airline_icao` | → | `routes.airline_id` | `airlines(id)` |

`routes.uq_route_segment UNIQUE (origin_iata, destination_iata, airline_icao)` must
be dropped and recreated as
`UNIQUE (origin_airport_id, destination_airport_id, airline_id)` — it references the
columns being removed, so it cannot survive unchanged.

**Cost accepted:** every read query across `DoobieAirportRepository`,
`DoobieAirlineRepository`, and `DoobieRouteRepository` that reconstructs a domain
object needing a business code (`CountryCode`, `IataCode`, `IcaoCode`) now needs a
`JOIN` to the parent table(s) to recover that code, where today it reads the natural
key directly off the same row. `routes` in particular goes from a single-table
`SELECT` to a four-way join (`routes` + `airports` ×2 + `airlines`). `save`/`update`
on every child table need their parent id(s) resolved from the incoming business
code(s) before the write — same pattern as originally designed for
`DoobieAirportRepository` (`SELECT id FROM <parent> WHERE code = ?` first, fail
`DomainError.<Parent>NotFound` on `None` before the insert/update runs, composed in
one `.transact`), now repeated for every FK column being introduced.

### Decision 3 — index strategy for the business finders (unchanged from original scope)

| Finder | Query shape | Index support after this plan |
|---|---|---|
| `Country.findByCode` / `Airport.findByIata` / `Airline.findByIcao` | exact match on natural key | `UNIQUE` constraint on `code`/`iata_code`/`icao_code` (btree, same lookup speed as today's PK) |
| `Country.searchByName` / `Airport.searchByName` | `ILIKE '%text%'` | new `pg_trgm` GIN index — today this is a full sequential scan on both tables |
| `Airport.findByCountry` | equality on the FK column | index on `country_id` (replaces `idx_airports_country`) |

`pg_trgm` (bundled with `postgres:16-alpine`, just needs
`CREATE EXTENSION IF NOT EXISTS pg_trgm`) added for `countries.name` and
`airports.name`. Not added for `airlines.name` — no `searchByName`-style finder
exists for Airline today (its HTTP endpoints are still stubs per `CLAUDE.md`), so
there's no query to accelerate yet; adding the index now would be speculative.
Caveat: trigram matching is weakest for patterns under 3 characters — a 3-char
pattern (the "country containing 3 letters" example) is right at the minimum
viable length, not the sweet spot, but still works.

## Migration plan

One new file, `V7__add_surrogate_keys.sql`. Ordering is load-bearing — Postgres
refuses to drop a constraint while another object depends on it, so every old FK
must be dropped **before** the PK/unique constraint it depends on, and every new FK
must be added **after** its target's new PK exists:

```sql
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

-- Phase 9: new indexes — FK equality lookups + business-search trigram indexes
CREATE INDEX idx_airports_country_id           ON airports (country_id);
CREATE INDEX idx_airlines_country_id           ON airlines (country_id);
CREATE INDEX idx_routes_origin_airport_id      ON routes (origin_airport_id);
CREATE INDEX idx_routes_destination_airport_id ON routes (destination_airport_id);
CREATE INDEX idx_routes_airline_id             ON routes (airline_id);
CREATE INDEX idx_countries_name_trgm ON countries USING GIN (name gin_trgm_ops);
CREATE INDEX idx_airports_name_trgm  ON airports  USING GIN (name gin_trgm_ops);
```

`GENERATED ALWAYS AS IDENTITY` chosen over `BIGSERIAL` — the SQL-standard,
Postgres-recommended form for new columns.

**Must verify before running by hand:** every constraint name above is Postgres's
default auto-generated name inferred from the V1–V4 `CREATE TABLE` shapes (inline
`PRIMARY KEY`/`REFERENCES`/named `CONSTRAINT uq_route_segment`), not confirmed
against the live container. Run `\d countries`, `\d airports`, `\d airlines`,
`\d routes` against the running dev Postgres first and correct any mismatched
names — a wrong guess fails loudly (`constraint ... does not exist`), it just means
this script isn't paste-and-run without that check.

## Application-code changes

1. **`QuillCountryRepository`** — add `id: Long` to the private `CountryRow`. No
   query bodies change (explicit column lists in `insert`/`update` already exclude
   `id`; unprojected reads pick up the extra column and simply never read it).
2. **`DoobieAirportRepository`** — every method's SQL changes:
   - `findByIata`, `findAll`, `searchByName`, `findByCountry`: add
     `JOIN countries c ON a.country_id = c.id`, select `c.code` in place of
     `a.country_code`.
   - `findByCountry`: filter becomes `WHERE c.code = ${code.value}`.
   - `save`/`update`: resolve `country_id` via
     `SELECT id FROM countries WHERE code = ?` first; `None` ⇒ fail
     `CountryNotFound` before the insert/update runs; keep the existing
     `FOREIGN_KEY_VIOLATION` catch as a concurrent-delete backstop.
3. **`DoobieCountryRepository`** (unwired) — no changes; explicit column selects
   already exclude `id`.
4. **`DoobieAirlineRepository`** (unwired) — same shape of change as
   `DoobieAirportRepository`: join to `countries` for reads, resolve `country_id`
   before `save`. (The pre-existing `foundation_date` bug is untouched — out of
   scope.)
5. **`DoobieRouteRepository`** (unwired) — largest change: reads need
   `routes JOIN airports ao ON r.origin_airport_id = ao.id JOIN airports ad ON r.destination_airport_id = ad.id JOIN airlines al ON r.airline_id = al.id`,
   selecting `ao.iata_code`, `ad.iata_code`, `al.icao_code` in place of the current
   direct columns. `save` needs all three ids (`origin_airport_id`,
   `destination_airport_id`, `airline_id`) resolved from the incoming
   `IataCode`/`IataCode`/`IcaoCode` before insert — three lookups instead of one.
6. No changes to `domain/`, `application/`, `adapter-http/`, `bootstrap/`.

## Verification

- `sbt scalafmtAll && sbt compile` — zero warnings (covers the unwired repositories
  too, since they still must compile).
- Manually re-apply schema to the dev Postgres container (this plan changes primary
  keys, so this needs a drop/recreate of the affected tables or careful manual
  application of the `ALTER`s in order — not a simple additive migration).
- Exercise existing HTTP endpoints end-to-end (`POST`/`GET`/`PUT` airports and
  countries, `GET /api/v1/countries/{code}/airports`) against the real Quill/Doobie
  repositories — this is the **only** regression check available, since no
  automated test touches real SQL (see baseline note above). Confirm the
  join-based reads and the resolve-before-insert `CountryNotFound` path behave
  identically to today.
- `DoobieAirlineRepository`/`DoobieRouteRepository` cannot be exercised end-to-end
  (unwired) — compile success is the only available check for those two.

## Files touched (summary)

- `infrastructure/migration/src/main/resources/db/migration/V7__add_surrogate_keys.sql` (new)
- `infrastructure/persistence-quill/.../QuillCountryRepository.scala` (row type only)
- `infrastructure/persistence-postgres/.../DoobieAirportRepository.scala`
- `infrastructure/persistence-postgres/.../DoobieAirlineRepository.scala`
- `infrastructure/persistence-postgres/.../DoobieRouteRepository.scala`
- `CLAUDE.md` — update the "Database schema" table with a `V7` row; adjust the
  schema summary lines for `airports`/`airlines`/`routes` (they currently describe
  the natural-key FK shape being replaced here)
