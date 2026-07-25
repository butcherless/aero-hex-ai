# Plan: Health-check endpoints (`/health/live`, `/health/ready`)

## Goal

Add liveness and readiness probes to the app. Neither ZIO/zio-http nor Tapir ship a dedicated
health-check module (no Spring-Actuator/SmallRye-Health equivalent), and Scala's stdlib has no
HTTP server to hang one off of — so this is a plain Tapir endpoint pair, following the same
per-resource shape as every other endpoint in this project (`XxxEndpoints` object + `XxxRoutes`
class + companion `layer`).

## Decisions

1. **Unversioned paths (`/health/live`, `/health/ready`) instead of `/api/v1/health/...`.**
   Every other endpoint in this project lives under `/api/v1`, but these two follow the
   Kubernetes/load-balancer liveness-and-readiness probe convention instead — infra tooling
   expects a stable, version-independent path. Confirmed Tapir/`ZioHttpInterpreter`/Swagger impose
   no shared prefix, so mixing unversioned and versioned paths in the same endpoint list and
   OpenAPI doc is safe.

2. **Liveness has error type `Unit` — no `EndpointErrors.unexpectedError` safety-net variant.**
   Every other endpoint in this project adds that variant even when "it shouldn't fail" (e.g.
   `RouteEndpoints.findByAirline`). Liveness does zero I/O and genuinely cannot fail, so
   documenting zero error responses is the more honest OpenAPI contract for a liveness probe.
   Rejected: reusing the standard `oneOf(...)` + `unexpectedError` pattern, which would falsely
   imply the endpoint can fail.

3. **Readiness's error body reuses `HealthStatus`, not `HttpErrorResponse`.** This isn't a
   `DomainError`-derived failure — no domain/application layer is involved at all — so the
   entity error vocabulary (`ErrorMapper`, `oneOf` variant matching) doesn't apply. A single fixed
   status code (503) needs no `oneOf` wrapper. Rejected: modeling readiness failure through
   `DomainError`/`ErrorMapper` for consistency with entity endpoints — rejected because it would
   invent a fake domain error for something that isn't a domain concept.

4. **Readiness pings Postgres via the shared HikariCP `DataSource`, requested as a plain
   `javax.sql.DataSource` (JDK type).** `adapter-http`'s `build.sbt` entry depends only on
   `domain, application`, not `persistence-quill` — requiring `DataSource` as an environment
   dependency doesn't create a compile dependency between them. `WiringModule` (composition root)
   supplies the real `QuillDataSourceLayer.live`, reusing the exact same pool instance already
   shared by the five Quill repositories (ZIO layer memoization within one composed `TaskLayer`).
   Rejected: giving `adapter-http` a direct dependency on `persistence-quill` just for this check
   — unnecessary, since the JDK interface is all that's needed.

5. **DB check: `conn.isValid(2)` after `getConnection()`, not just obtain-and-close.** HikariCP
   doesn't validate a connection on every checkout, so a bare `getConnection()`/`close()` can
   report healthy on a stale connection. `isValid(2)` is a real 2s-timeout JDBC4 round-trip check.

6. **Readiness is bounded by `.disconnect.timeoutFail(...)(3.seconds)` — added during
   implementation, elevated from an initially-flagged "optional follow-up" to required.** Live
   testing against the real app (Postgres stopped via `docker compose stop postgres`) showed
   `/health/ready` hanging for HikariCP's default 30s `connectionTimeout` before returning 503 —
   useless as a fast probe. `ZIO.attemptBlocking`'s underlying JDBC call can't be force-stopped by
   fiber interruption (the JVM thread keeps blocking), so `.timeoutFail` alone still waited the
   full 30s for the blocking call to finish before returning (confirmed by timing it: exactly
   30.0s). Adding `.disconnect` lets the timeout race return early while the blocked call is
   interrupted in the background — confirmed live: `/health/ready` now returns 503 in ~3s with
   Postgres down. Rejected: leaving the 30s hang — technically correct but useless for any real
   deployment's probe timeout (typically 1-10s).

## Implementation summary

New package `adapter-http/.../health/`:
- `HealthStatus.scala` — shared response DTO (`{"status": "UP"}`/`{"status": "DOWN"}`), reused for
  both endpoints' success/failure bodies.
- `HealthEndpoints.scala` — `live: PublicEndpoint[Unit, Unit, HealthStatus, Any]` and
  `ready: PublicEndpoint[Unit, HealthStatus, HealthStatus, Any]`, both under `"health" / "live"` /
  `"health" / "ready"`, no shared `base`.
- `HealthRoutes.scala` — `class HealthRoutes(dataSource: DataSource)` with a `checkDb: Task[Unit]`
  (`attemptBlocking` + `isValid(2)` + `.disconnect.timeoutFail(...)(3.seconds)`), wired into both
  endpoints' `zServerLogic`, plus companion `val layer: URLayer[DataSource, HealthRoutes]`.

Wiring:
- `HttpServer.scala` — `AppRoutes` type alias extended with `& HealthRoutes`; `health.serverEndpoints`
  appended to `business`.
- `WiringModule.scala` — `(QuillDataSourceLayer.live >>> HealthRoutes.layer)` appended to `appLayer`.
- `ApiSpec.scala` — `"Health"` tag added to `topLevelTags`; `HealthEndpoints.live`/`.ready` added to
  `allEndpoints` (required for the static `OpenApiGenerator`/`validate-openapi`/
  `sync-postman-collection` path, which is independent of `HttpServer`'s live route wiring).

Tests: `adapter-http/src/test/.../health/HealthEndpointsSpec.scala` — `TapirStubInterpreter` over
`HealthRoutes`, with hand-rolled `DataSource`/`Connection` fakes (a JDK dynamic `Proxy` for
`Connection`, since no mocking library exists in this project and `Connection` has ~60 abstract
methods). Covers: `/health/live` always 200; `/health/ready` 200 with a working fake, 503 with a
failing fake; a `HealthRoutes.layer` wiring smoke test (2 endpoints).

Docs: `docs/api/endpoint-status.md` — new `Health` section, with a note that these two are the
sole intentionally unversioned endpoints.

## Verification performed

- `sbt scalafmtAll && sbt compile` — zero errors/warnings.
- `sbt test` — full suite green (350 tests total across all modules, including the 4 new health
  tests).
- Live-verified against the real app (real Postgres, rebuilt assembly jar):
  - `/health/live` → 200 regardless of Postgres state.
  - `/health/ready` → 200 with Postgres up.
  - `/health/ready` → 503 in ~3s with Postgres stopped (confirmed the `.disconnect.timeoutFail`
    fix — without it, this took the full 30s of HikariCP's `connectionTimeout`).
- `validate-openapi` skill — PASSED (0 Redocly errors, 0 inline schemas, 0 Spectral errors); both
  endpoints appear correctly in the endpoint inventory under the "Health" resource, unversioned.
- `sync-postman-collection` skill — new `Health` folder (Liveness probe, Readiness probe) added to
  `docs/api/collection.json`; every other folder's diff was pure id/example regeneration noise,
  discarded by the noise filter. Newman's E2E verification step failed, but for reasons unrelated
  to this change: the shared dev Postgres already has real master data synced in (all 249
  countries, including Portugal and Kiribati with its real airports), which collides with the E2E
  fixtures' hardcoded assumption that those country codes don't yet exist (`POST` returns 409
  instead of 201, cascading into every downstream assertion in those folders). None of the
  failures touch `/health/*`. Per the user's direction, the Postman sync was kept without a clean
  Newman pass, since the DB-state conflict is a pre-existing environment issue, not a regression.

## Files touched

- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/health/HealthStatus.scala` (new)
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/health/HealthEndpoints.scala` (new)
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/health/HealthRoutes.scala` (new)
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/server/HttpServer.scala`
- `bootstrap/src/main/scala/dev/cmartin/aerohex/bootstrap/WiringModule.scala`
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/ApiSpec.scala`
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/health/HealthEndpointsSpec.scala` (new)
- `docs/api/endpoint-status.md`
- `docs/api/collection.json` (via `sync-postman-collection` skill)
