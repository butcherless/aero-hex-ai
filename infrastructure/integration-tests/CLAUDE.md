# Integration tests (opt-in, real Postgres)

Runs `FlywayMigration` and `QuillXxxRepository` against real Postgres through Testcontainers, with no stubs. It's deliberately **not** in `root`'s `.aggregate(...)`, so `sbt compile`, `sbt test` and `sbt coverageAggregate` never touch it.

```bash
sbt integrationTests/test   # or: sbt integrationTest (alias)
```

Specs: `FlywayMigrationItSpec` plus `Quill<Entity>RepositoryItSpec` for Country, Airport, Airline, Aircraft, Flight, Route, User and RevokedToken. `plans/add-persistence-integration-tests.md` has the scope and design rationale.

When writing a spec:

- **Seed parent rows first.** FKs target surrogate ids, so Airport and Airline need a Country, Aircraft needs an Airline, and Flight and Route need Airports (Flight also an Airline).
- **Test deleting a referenced row** where one exists: it must fail with the domain error (`CountryInUse`, `AirportInUse`, `AirlineInUse`), not crash on the FK violation. This requires widening the contract spec's env type with the child repository.
- **`UserRepository` has no `save`**, so its "found" case seeds through raw JDBC on the shared `DataSource` (`plans/security/login.md`, decision 5).

Two gotchas, explained in the plan doc:

- **`build.sbt` sets `Test / javaOptions += "-Dapi.version=1.41"`.** Testcontainers' Docker probe otherwise falls back to an old API version that recent Docker Desktop rejects, which surfaces as a misleading "Could not find a valid Docker environment".
- **Every spec must call `.provideLayerShared(...)`, never `.provideLayer(...)`, on its `suite(...)`.** `provideLayer` silently rebuilds the layer per `test`, starting a new Postgres container each time.
