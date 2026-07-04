# Plan: manual verification runbook for the database/persistence changes

## Goal

A repeatable, copy-pasteable runbook to manually confirm — via a clean rebuild and
real HTTP calls against the running server — that the Country/Airport endpoints
read/write real data through the post-surrogate-key persistence layer (Quill for
Country, Doobie for Airport). This documents the exact steps already run once by
hand during this session (see the surrogate-key migration work in
`plans/surrogate-long-keys-country-airport.md`), as a plan to run again after future
persistence-layer changes — not a one-off.

## Preconditions

- Docker running, `docker compose up -d postgres` applied, dev database already has
  `V1`–`V7` migrations applied (see `plans/surrogate-long-keys-country-airport.md`)
  and the seed data from `plans/seed-data-countries-airports.sql` loaded.
- No prior instance of the app running.

## Steps

### a) Stop any running instance

```bash
pkill -f "dev.cmartin.aerohex.bootstrap.Main" 2>/dev/null || true
```

Per `CLAUDE.md`'s "Running the application" note — always kill before restarting.

### b) Clean build (fat JAR)

```bash
sbt ";clean;bootstrap/assembly"
```

Expect `[success]` and a jar at
`target/out/jvm/scala-3.3.8/bootstrap/bootstrap-assembly-*.jar`. A clean build
(rather than an incremental one) matters here specifically because this is
verifying a persistence-layer change — incremental compilation could mask a stale
class file from before the schema/repository change.

### c) Start the application and confirm it's up

```bash
JAR=$(find target/out -name "bootstrap-assembly-*.jar" | sort | tail -1)
java -cp "$JAR" dev.cmartin.aerohex.bootstrap.Main > /tmp/aerohex-server.log 2>&1 &
sleep 5
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/docs/docs.yaml   # expect 200
```

### d) Retrieve all countries

```bash
curl -s http://localhost:8080/api/v1/countries
```

Expected shape (real seed data, order by `code`):

```json
[{"code":"DE","name":"Germany"},{"code":"ES","name":"Spain"},{"code":"FR","name":"France"},{"code":"GB","name":"United Kingdom"}, ...]
```

Confirms `QuillCountryRepository.findAll` reads through the `id BIGINT` PK schema
without the domain-visible shape changing.

### e) Retrieve a country by its code

```bash
curl -s http://localhost:8080/api/v1/countries/ES
```

Expected: `{"code":"ES","name":"Spain"}`. Confirms `findByCode` still works by
natural key (`code`) even though it's no longer the literal PK.

### f) Retrieve all airports

```bash
curl -s http://localhost:8080/api/v1/airports
```

Expected: full list of seeded airports, each with a `countryCode` field
(`"MAD"`/`"ES"`, `"LHR"`/`"GB"`, etc.) — confirms `DoobieAirportRepository.findAll`'s
new `JOIN countries c ON a.country_id = c.id` correctly reconstructs `CountryCode`
for every row, exactly as before the migration from the API consumer's point of
view.

### g) Retrieve all airports in the UK

```bash
curl -s http://localhost:8080/api/v1/countries/GB/airports
```

Expected (real output, captured this session):

```json
[{"iata":"EDI","icaoCode":"EGPH","name":"Edinburgh Airport","city":"Edinburgh","countryCode":"GB"},
 {"iata":"LGW","icaoCode":"EGKK","name":"London Gatwick Airport","city":"London","countryCode":"GB"},
 {"iata":"LHR","icaoCode":"EGLL","name":"London Heathrow Airport","city":"London","countryCode":"GB"},
 {"iata":"MAN","icaoCode":"EGCC","name":"Manchester Airport","city":"Manchester","countryCode":"GB"},
 {"iata":"STN","icaoCode":"EGSS","name":"London Stansted Airport","city":"London","countryCode":"GB"}]
```

Confirms `FindAirportsByCountryService` → `DoobieAirportRepository.findByCountry`'s
rewritten join-and-filter-by-`c.code` query, and the 404-on-unknown-country check
against the real `CountryRepository` (Doobie-backed as of the Quill→Doobie wiring
switch — see `CLAUDE.md`'s module dependency graph).

### h) Print results to console for review

Two ways to render each of d)–g) for readable console review:

**Pretty-printed JSON** — `python3 -m json.tool` (or `jq .`):

```bash
curl -s http://localhost:8080/api/v1/countries | python3 -m json.tool
```

**Table mode** (preferred for eyeballing several rows/columns at once) — `jq` to
extract a header row + fields as TSV, piped through `column -t`:

```bash
curl -s http://localhost:8080/api/v1/countries | \
  jq -r '["CODE","NAME"], (.[] | [.code, .name]) | @tsv' | column -t -s $'\t'

curl -s http://localhost:8080/api/v1/airports | \
  jq -r '["IATA","ICAO","NAME","CITY","COUNTRY"], (.[] | [.iata, .icaoCode, .name, .city, .countryCode]) | @tsv' | column -t -s $'\t'

curl -s http://localhost:8080/api/v1/countries/GB/airports | \
  jq -r '["IATA","ICAO","NAME","CITY","COUNTRY"], (.[] | [.iata, .icaoCode, .name, .city, .countryCode]) | @tsv' | column -t -s $'\t'
```

Expected table output (captured this session, `GB` airports):

```
IATA  ICAO  NAME                     CITY        COUNTRY
EDI   EGPH  Edinburgh Airport        Edinburgh   GB
LGW   EGKK  London Gatwick Airport   London      GB
LHR   EGLL  London Heathrow Airport  London      GB
MAN   EGCC  Manchester Airport       Manchester  GB
STN   EGSS  London Stansted Airport  London      GB
```

## Optional extra checks (not requested, but cheap given the app is already up)

- `GET /api/v1/airports/search?q=Heathrow` — exercises the new `pg_trgm`-indexed
  `ILIKE` search.
- `POST /api/v1/airports` with an unknown `countryCode` — expect `404` with
  `{"message":"Country not found: ..."}"`, confirming the `resolveCountryId`
  fail-fast path added during the surrogate-key work.

## Cleanup

```bash
pkill -f "dev.cmartin.aerohex.bootstrap.Main" 2>/dev/null || true
```

Leave `docker compose`'s `postgres` container running unless the session is fully
done — stopping/removing it is a separate, more disruptive step not part of this
runbook.
