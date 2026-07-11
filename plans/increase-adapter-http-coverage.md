# Plan: Increase `adapter-http` unit-test coverage

## Goal

Close the concrete, currently-unexercised statement/branch gaps in `adapter-http`'s `dto`,
`error`, and `endpoint` packages — prioritizing the highest coverage-per-effort work first —
without changing production behavior. No new abstractions unless they pay for themselves the
same way `plans/refactor-country-airport-tests.md` already did (stub-factory pattern,
`TapirStubInterpreter` + `BackendStub`).

## Current state (confirmed via Explore + a fresh, scoped `sbt adapterHttp/testOnly *; sbt coverageAggregate` run)

Only **two** dedicated endpoint spec files exist: `CountryEndpointsSpec.scala` and
`AirportEndpointsSpec.scala` (40 tests total, 20 each). **Airline, Aircraft, Flight,
FlightInstance, and Route have zero dedicated test files** — despite Airline being a fully
"implemented" (Quill-backed) resource per `docs/api/endpoint-status.md`, not just a stub.

**Measurement-integrity caveat, worth fixing before re-baselining:** `coverageDataDir` is
deliberately kept outside `target/` (`.coverage-data/`, see `build.sbt:27` and
`CLAUDE.md:247`) so `sbt clean` never wipes it. That also means measurement data accumulates
across ad-hoc local `sbt run` / manual-curl verification sessions and is never reset.
Concretely: `RouteRoutes.scala` and `RouteDto.scala` currently show 100% statement coverage
in the aggregate report even though **no `RouteEndpointsSpec.scala` file exists** — the only
plausible explanation is residual measurement data from an earlier manual `sbt run` + curl
session against `POST /api/v1/routes`. This is not reproducible from a clean CI checkout
running only `sbt test`. Run `rm -rf adapter-http/.coverage-data` (and ideally every module's)
before re-measuring, so before/after deltas in this plan's acceptance criteria are
attributable solely to the automated suite.

### Package-by-package findings (statement counts exact, from the scoverage class table)

**`adapter.http.endpoint` — 275/307 (89.58%).** Every `*Endpoints` object (the Tapir endpoint
*definitions*) shows 100%, because merely constructing a `*Routes` class forces Scala object
initialization of the referenced `*Endpoints` object — that happens even without a real HTTP
request. The actual gap is in the `zServerLogic` lambda bodies inside `AirlineRoutes.scala`,
`AircraftRoutes.scala`, `FlightRoutes.scala`, `FlightInstanceRoutes.scala` — each shows exactly
5/13 (38.46%), i.e. exactly the 8 statements per file inside the lambda bodies are uncovered.
4 files × 8 statements = 32, exactly matching 307 − 275 = 32. These only execute when a request
is actually routed through `TapirStubInterpreter`, i.e. only a real spec file closes this gap.

**`adapter.http.dto` — 29/51 (56.86%).** `AircraftDto.scala:10-15` (`fromDomain`, 4 stmts),
`AirlineDto.scala:10-15` (4 stmts), `FlightDto.scala:16-24` (7 stmts),
`FlightInstanceDto.scala:15-22` (7 stmts) are all at 0% — exactly 22 statements, exactly
matching 51 − 29 = 22. `CountryDto`, `AirportDto` (+ their `CreateXRequest`/`UpdateXRequest`),
and (per the stale-data caveat above) `RouteDto` show 100%.

**`adapter.http.error` — 38/63 (60.32%).** `EndpointErrors.scala` is 100% (21/21 stmt, 3/3
branch — the `oneOfVariantValueMatcher` predicates run on every response encode in the
existing Country/Airport tests). `ErrorMapper.scala` is 17/42 stmt (40.48%), 4/12 branch
(33.33%). The 12 branches are the 12 `DomainError` match arms (`ErrorMapper.scala:15-26`);
exactly the 4 covered by today's tests (`CountryNotFound`, `CountryAlreadyExists`,
`AirportNotFound`, `AirportAlreadyExists`) are green. The other 8 —
`AirlineNotFound`/`AirlineAlreadyExists` (`:19-20`), `RouteNotFound` (`:21`), `AircraftNotFound`
(`:22`), `FlightNotFound` (`:23`), `FlightInstanceNotFound` (`:24`), `RouteAlreadyExists`
(`:25`), `InvalidRoute` (`:26`) — are all red. `toMessage` (`ErrorMapper.scala:29`) is dead code
from production's perspective: only `ErrorMapper.toHttpError` is called anywhere in `main`
(every `*Routes.scala` file); `toMessage` is only reachable by a direct unit test.

Two of the 8 untested `DomainError` cases are structurally unreachable via HTTP today, not
just untested:
- `AirlineAlreadyExists` — `AirlineEndpoints.scala` has no create endpoint (find-only resource).
- `RouteNotFound` — `RouteEndpoints.scala` has only `create`; its
  `notFoundVariant("Airport or airline not found.")` maps FK-lookup failures
  (`AirportNotFound`/`AirlineNotFound`), not `RouteNotFound` itself (no `GET /routes/{id}`
  yet). A direct `ErrorMapper` unit test is the only way to cover these two.

**`adapter.http.server` — 17/19 (89.47%).** Not touched by this plan — `HttpServer.serve`'s
uncovered 2 statements are the real `zio-http` `Server.serve(...)` call and error handler
wiring, which would need a live-server integration test (out of scope; `adapter-http` has no
such infra today and building it is disproportionate to the gain).

### Validator/branch gaps (explains the project's low 34.18% overall branch coverage)

Broader than "Airline/Aircraft/Flight/FlightInstance/Route lack validators" — every currently
validated field in Country and Airport, the two "hardened" resources, only has one of its 2-3
validator branches exercised:

- `CountryEndpoints.codeParam` (`CountryEndpoints.scala:15-19`: `minLength(2)`,
  `maxLength(2)`, `pattern`) — only the too-short case (`"X"`) is tested. No test drives a
  too-long code (e.g. `"ESP"`) or a pattern-mismatch (e.g. digits `"12"`).
- `AirportEndpoints.iataParam` / `countryCodeParam` (`AirportEndpoints.scala:15-19,23-27`) —
  same shape: only too-short tested, not too-long or pattern-mismatch.
- `CreateCountryRequest`/`UpdateCountryRequest` body fields (`CountryDto.scala:24-32,41-44`)
  and `CreateAirportRequest`/`UpdateAirportRequest` body fields
  (`AirportDto.scala:48-77,92-114`) — each "invalid body" test only violates one field
  (`code`/`icaoCode`) by making it too short; `name`'s `minLength(1)` and `city`'s
  `minLength(1)` are never violated, and no field's `maxLength`/`pattern` branch is
  independently exercised.
- Pagination validators — `CountryEndpoints.findAll`'s `page`/`pageSize`
  (`Validator.min(1)`/`Validator.max(100)`, `CountryEndpoints.scala:48-54`) and
  `AirportEndpoints.findByCountry`'s equivalent (`AirportEndpoints.scala:~94-101`) are never
  driven out of range (`pageSize=0`, `pageSize=101`, `page=0`). Separately,
  `AirportEndpoints.findAll`'s `page`/`pageSize` (`AirportEndpoints.scala:46-47`) have **no
  `Validator` at all**, unlike `CountryEndpoints.findAll` and `AirportEndpoints.findByCountry`
  — a real, pre-existing, small inconsistency, not just a test gap.

For the 5 untested resources, the validator picture is mixed, not uniform:
- `AirlineEndpoints.icaoParam` (`AirlineEndpoints.scala:15-20`) — already has the full
  `minLength/maxLength/pattern` triple (added in commit `47cafbe`). Just needs boundary tests.
- `AircraftEndpoints.findByRegistration`'s path param (`AircraftEndpoints.scala:29`) — no
  validator at all. Unlike IATA/ICAO/ISO codes, aircraft registrations have no fixed
  international length/pattern (`"EC-MIG"`, `"N12345"`, etc. all differ by country) — imposing
  a strict length/pattern validator here would be incorrect, not just untested. Leave
  unvalidated.
- `FlightEndpoints.findByCode`'s path param (`FlightEndpoints.scala:29`) — same reasoning:
  flight codes vary in length (2-letter airline prefix + 1-4 digit number). Leave unvalidated.
- `FlightInstanceEndpoints.findById`'s path param (`FlightInstanceEndpoints.scala:29`) — a
  genuine, fixable gap: `FlightInstanceId` is a UUID-backed opaque type with a fixed,
  well-known format. Add a `Validator.pattern` here, mirroring the `iataParam`/`codeParam`
  precedent, then test the invalid-UUID-string → 400 case.
- `CreateRouteRequest` (`RouteDto.scala:15-20,59-82`) — `originIata`/`destinationIata`/
  `airlineIcao` already have `minLength(3)/maxLength(3)`, `distanceKm` has `Validator.min(1)`
  — needs the same boundary-case test treatment as Country/Airport once `RouteEndpointsSpec`
  exists.

## Design decisions

**1. `error/ErrorMapper` — add a direct unit-test spec, not just indirect endpoint coverage.**
New `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/error/ErrorMapperSpec.scala`
constructing each of the 12 `DomainError` cases directly and asserting `ErrorMapper.toHttpError`'s
resulting `(StatusCode, HttpErrorResponse)`, plus one direct assertion on `toMessage`. This is
necessary, not just convenient: `AirlineAlreadyExists` and `RouteNotFound` are structurally
unreachable via any endpoint that exists or is planned here, so no amount of endpoint-spec work
will ever cover them. For the other 6 cases, the new endpoint specs (decision 3) will
incidentally re-exercise the same lines — welcome redundancy, not a reason to skip this spec:
cheaper to write (~12 small tests, no HTTP-stub plumbing), decouples "is the error-mapping table
correct" from "is the Tapir wiring correct."
*Rejected:* rely solely on indirect coverage via the 5 new endpoint specs — leaves 2 of 12
branches permanently uncovered and couples a trivial pure-function test to expensive HTTP-stub
infrastructure for no benefit.

**2. `dto/*` — extend endpoint-spec fixtures, do not add dedicated DTO unit-test files.**
DTOs here are genuinely trivial (`case class` + one-line `fromDomain` field copy, no branching
beyond `Option`/opaque-type unwrapping). A dedicated `AirlineDtoSpec`-style file would be fast
but fully redundant with what the mandatory new endpoint specs (decision 3) already build (a
`GET` response naturally round-trips through `fromDomain`). Close the `dto` gap purely as a side
effect of decision 3, with fixtures chosen to hit field variety that matters (e.g.
`FlightEndpointsSpec`'s fixture list should include one `Flight` with `alias = Some(...)` and
one with `alias = None`, since `FlightDto.alias` is the only `Option`-typed DTO field in the
module). Matches existing convention — no `dto/*Spec.scala` files exist today, and
`CountryDto`/`AirportDto` already reached 100% purely via their endpoint specs.
*Rejected:* add `dto/*Spec.scala` per resource — would duplicate coverage the mandatory endpoint
specs already produce and adds a test-file convention unused elsewhere in the module. Revisit if
a future DTO gains real branching logic (`Either`-based validation, computed defaults, etc.).

**3. Five new `*EndpointsSpec.scala` files — the bulk of the real work, same idiom as `AirportEndpointsSpec`.**
One file per untested resource (`AirlineEndpointsSpec`, `AircraftEndpointsSpec`,
`FlightEndpointsSpec`, `FlightInstanceEndpointsSpec`, `RouteEndpointsSpec`), each using the
established `TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any])).whenServerEndpointsRunLogic(new XRoutes(...).serverEndpoints).backend()`
+ `makeBackend(...)`-with-named-defaults pattern. `FindAirlineUseCase`/`FindAircraftUseCase`/
`FindFlightUseCase`/`FindFlightInstanceUseCase` each have 2 abstract methods — implement via
`new FindXUseCase: ...` anonymous instances (matching `AirportEndpointsSpec`'s
`defaultFind`/`notFoundFind`), not SAM lambdas. `CreateRouteUseCase` has exactly 1 method —
SAM-lambda-able (matching `CountryEndpointsSpec`'s `defaultCreate`/`conflictCreate`).
*Rejected:* a single resource-agnostic parameterized spec to cut boilerplate across the 4
structurally-identical find-only resources — `plans/refactor-country-airport-tests.md` already
establishes this codebase prefers per-resource spec files even at some repetition cost, and the
4 resources aren't quite identical (different DTOs, different validator situations). Revisit
only if a 6th find-only resource appears.

**4. Validator gap for `FlightInstanceEndpoints.findById`.**
Add a UUID-shaped `Validator.pattern` to the `id` path param (mirroring `iataParam`/
`codeParam`/`icaoParam`), then add the invalid-UUID → 400 test. Leave
`AircraftEndpoints.findByRegistration` and `FlightEndpoints.findByCode` path params
unvalidated — international registration/flight-code formats aren't fixed-length/fixed-pattern.
*Rejected:* validate all three path params uniformly "for consistency" — would reject
legitimate registrations/flight codes.

**5. `AirportEndpoints.findAll` missing pagination validators.**
Nit-fix: add `.validate(Validator.min(1))`/`.validate(Validator.max(100))` to `page`/`pageSize`
in `AirportEndpoints.scala:46-47`, matching `CountryEndpoints.findAll` and
`AirportEndpoints.findByCountry`. Low priority (2 branches) — optional, not blocking.

## Implementation steps, in priority order (highest coverage-per-effort first)

**Step 0 — Establish a clean baseline.** `rm -rf adapter-http/.coverage-data` (and any other
module's, if measuring the full aggregate), then `sbt compile; sbt adapterHttp/testOnly *;
sbt adapterHttp/coverageReport` (per-module) and/or `sbt coverageAggregate` (whole project) to
get numbers reflecting only the automated suite. Re-run after each step to track real deltas,
not stale-data artifacts. Follow the `mkdir -p <module>/.coverage-data/scoverage-data`
workaround from `CLAUDE.md` if `ExceptionInInitializerError` appears.

**Step 1 — `error/ErrorMapperSpec.scala` (new file).** 12 tests, one per `DomainError` case,
asserting the mapped `StatusCode` (and message content — at least a `contains` check on the
identifying field), plus 1 test for `toMessage`. Estimated: `error` package 60.3% → ~98-100%
stmt, 33.3% → 100% branch (12/12). Smallest file, no HTTP-stub plumbing, immediately closes the
two structurally-unreachable-via-HTTP cases. Do this first.

**Step 2 — Extend `CountryEndpointsSpec.scala` and `AirportEndpointsSpec.scala` with boundary
tests.** No new files, reuses existing `makeBackend(...)` factories. Add, per resource:
too-long path-param test, pattern-mismatch path-param test, empty-string `name`/`city`
body-field test, and out-of-range pagination tests (`pageSize=0`, `pageSize=101`, `page=0`) for
`CountryEndpoints.findAll` and `AirportEndpoints.findByCountry`. Doesn't move `endpoint`
*statement* coverage much, but is the single highest-value contribution to the project's cited
34.18% branch coverage — likely +10-15 branches for near-zero new infrastructure cost.

**Step 3 — `endpoint/AirlineEndpointsSpec.scala` (new file).** Fixture:
`iberia = Airline(IcaoCode("IBE"), "Iberia", LocalDate.of(1927, 6, 28))`. Tests: `findAll`
(200 + pagination), `findByIcao` (200/404), `icaoParam` boundary cases, `AirlineRoutes.layer`
wiring test. Closes `AirlineRoutes`' 8 untested statements + `AirlineDto`'s 4 statements +
re-exercises `ErrorMapper`'s `AirlineNotFound` branch. Prioritized above Aircraft/Flight/
FlightInstance because Airline is a real, DB-backed feature, not a stub.

**Step 4 — `endpoint/FlightInstanceEndpointsSpec.scala` (new file) + the UUID validator fix
(decision 4).** First add the `Validator.pattern` to `FlightInstanceEndpoints.scala:29`, then
write the spec: `findAll` (200), `findById` (200/404/400-invalid-UUID). Fixture:
`FlightInstance(FlightInstanceId.generate, LocalDateTime.of(...), LocalDateTime.of(...), FlightCode("UX9117"), Registration("EC-MIG"))`.
Closes `FlightInstanceRoutes`' 8 statements + `FlightInstanceDto`'s 7 statements + adds a new
validator branch.

**Step 5 — `endpoint/FlightEndpointsSpec.scala` (new file).** Fixture list: two flights — one
with `alias = Some("AEA9117")`, one with `alias = None` — to cover both sides of `FlightDto`'s
only `Option` field via the `findAll` response. Tests: `findAll` (200), `findByCode` (200/404).
Closes `FlightRoutes`' 8 statements + `FlightDto`'s 7 statements.

**Step 6 — `endpoint/AircraftEndpointsSpec.scala` (new file).** Fixture:
`Aircraft(Registration("EC-MIG"), "B788", IcaoCode("IBE"))`. Tests: `findAll` (200),
`findByRegistration` (200/404) — no validator boundary case per decision 4. Closes
`AircraftRoutes`' 8 statements + `AircraftDto`'s 4 statements.

**Step 7 — `endpoint/RouteEndpointsSpec.scala` (new file).** Tests: `create` 201, 409 (conflict
— `DomainError.RouteAlreadyExists`), 404 (airport-or-airline-not-found via
`AirportNotFound`/`AirlineNotFound`), 400 (invalid body), plus boundary tests for
`originIata`/`destinationIata`/`airlineIcao` (too-long/pattern-mismatch) and `distanceKm`
(`Validator.min(1)`, e.g. `distanceKm = 0` → 400). `RouteRoutes.layer` wiring test. Note in the
PR description that this step's measured delta may look artificially small in a stale-data
comparison (per the Step 0 caveat) even though it's real, first-time automated coverage for a
mutating (`POST`) endpoint.

**Step 8 (optional, low priority — decision 5).** Add pagination validators to
`AirportEndpoints.findAll` (`AirportEndpoints.scala:46-47`), plus 2 boundary tests. Skip first
if time-constrained.

**After each step:** `sbt scalafmtAll && sbt compile` (zero warnings), then
`sbt "adapterHttp/testOnly *"` confirming all prior tests still pass plus the new ones, then
re-run coverage (Step 0's commands) to record the actual delta before moving on — makes it easy
to stop early since steps are ordered by expected value.

## Acceptance criteria

- `sbt compile` and `sbt scalafmtAll` clean, zero warnings.
- All prior 40 `adapter-http` tests still pass, unchanged in name/assertion (additive work).
- `adapter.http.error` package statement coverage ≥95%, branch coverage 100% (12/12
  `DomainError` cases).
- `adapter.http.dto` package statement coverage ~100% (all `fromDomain` methods exercised).
- `adapter.http.endpoint` package statement coverage ~100% (`AirlineRoutes`/`AircraftRoutes`/
  `FlightRoutes`/`FlightInstanceRoutes` lambda bodies covered; `RouteRoutes`/`RouteDto`
  coverage now backed by a real spec, not stale local-run data).
- Each of the 5 new resources has a dedicated `*EndpointsSpec.scala` file following the
  existing `makeBackend(...)` idiom.
- Coverage numbers reported are taken from a clean baseline (Step 0), not raw `.coverage-data`
  residue.

## Files touched (summary)

- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/error/ErrorMapperSpec.scala` (new)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/AirlineEndpointsSpec.scala` (new)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/AircraftEndpointsSpec.scala` (new)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/FlightEndpointsSpec.scala` (new)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/FlightInstanceEndpointsSpec.scala` (new)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/RouteEndpointsSpec.scala` (new)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/CountryEndpointsSpec.scala` (extended — boundary tests)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/AirportEndpointsSpec.scala` (extended — boundary tests)
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/endpoint/FlightInstanceEndpoints.scala` (edit — add UUID path validator)
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/endpoint/AirportEndpoints.scala` (edit, optional — add missing pagination validators to `findAll`)
