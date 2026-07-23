# Integration tests (opt-in, real Postgres)

`infrastructure/integration-tests/` runs `FlywayMigration`, `DoobieXxxRepository`, and
`QuillXxxRepository` against a real Postgres started via Testcontainers — no in-memory stubs, no
Tapir stub server. It is deliberately **not** in `root`'s `.aggregate(...)`, so `sbt compile`,
`sbt test`, and `sbt coverageAggregate` never touch it; invoke it explicitly:

```bash
sbt integrationTests/test   # or: sbt integrationTest (alias)
```

Coverage so far: `FlywayMigrationItSpec` (migrations reach `V15`), Country (`DoobieCountryRepositoryItSpec`
+ `QuillCountryRepositoryItSpec`, incl. `validateCode` success/failure against the `country_codes`
master table), Airport (`DoobieAirportRepositoryItSpec` +
`QuillAirportRepositoryItSpec`), Airline (`DoobieAirlineRepositoryItSpec` +
`QuillAirlineRepositoryItSpec`), Aircraft (`DoobieAircraftRepositoryItSpec` +
`QuillAircraftRepositoryItSpec`, seeding a `Country` then an `Airline` first since `aircraft.airline_id`
FKs to `airlines.id`), Flight (`DoobieFlightRepositoryItSpec` + `QuillFlightRepositoryItSpec`, seeding a
`Country`, two `Airport`s (origin + destination), and an `Airline` first since `flights.origin_airport_id`/
`destination_airport_id`/`airline_id` FK to `airports.id`/`airlines.id`) — each seeding its own `Country`
row first since `airports.country_id`/`airlines.country_id` FK to `countries.id` — 112 tests total, all
green. Route is not implemented yet.
See `plans/add-persistence-integration-tests.md` for the full scope table and design rationale (why a
plain subproject instead of sbt's deprecated `IntegrationTest` config, why one module instead of
three, why fresh-container-per-suite).

Two gotchas baked into the setup, both documented with why in the plan doc:
- `build.sbt` sets `Test / javaOptions += "-Dapi.version=1.41"` because Testcontainers 1.21.x's
  Docker-environment probe falls back to a hardcoded, very old API version when none is negotiated,
  and recent Docker Desktop releases reject that below their `MinAPIVersion` — surfacing as a
  misleading "Could not find a valid Docker environment" with no obvious cause unless you add a
  temporary SLF4J binding to see the underlying 400 from the daemon.
- **Every spec must call `.provideLayerShared(...)`, never `.provideLayer(...)`,** on its
  `suite(...)`. `provideLayer` silently rebuilds the layer per `test` block instead of once per
  suite — caught during validation when 15 Country tests started 16 separate Postgres containers
  instead of 3.
