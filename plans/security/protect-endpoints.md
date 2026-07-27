# Plan: Protect every business endpoint with the JWT issued by login (Step 2)

> **Status:** Implemented and verified live against a real local Postgres. Step 2 of the security
> roadmap tracked in `docs/todo/auth-jwt.md`'s `## Roadmap` section, building on step 1
> (`plans/security/login.md`).

## Goal

Every `/api/v1/*` endpoint across all seven resources (Country, Airport, Airline, Aircraft,
Flight, FlightInstance, Route) now requires a valid `Authorization: Bearer <token>` header —
reads and writes alike, confirmed with the user (no read/write split for this step; that's a
finer-grained distinction than plain authentication, closer to step 3's territory). Two
endpoints stay public by necessity: `/health/*` (probe convention, must work with no token) and
`POST /api/v1/auth/login` (the only way to *get* a token in the first place). No role/permission
distinction yet — any valid token grants access to everything; that's step 3.

## Mechanism (already decided, confirmed against Tapir's own docs earlier in this rollout)

Tapir's two-phase security — `securityIn(auth.bearer[String]())` + `zServerSecurityLogic` — not
a raw `zio-http` `HandlerAspect` (`plans/security/login.md` decision 4 already ruled this out:
a `HandlerAspect` wrapping the interpreted routes would validate tokens entirely outside Tapir,
so the 401 would never appear in the generated OpenAPI spec).

`TokenService.validate` already exists (`infrastructure/security`'s `JwtService`, unit-tested in
step 1) — this step's only job is wiring it into the HTTP layer as an actual gate. No new
`TokenService`/`DomainError` cases needed; `DomainError.InvalidToken`/`TokenExpired` and
`ErrorMapper`'s mapping to `401` were already added (unreachable) in step 1 specifically so this
step could reach them.

## Decisions

### 1. `securityIn` attaches to the same `val` used for both OpenAPI generation and the live server

Every `XxxEndpoints.scala` `val` (e.g. `AirlineEndpoints.findAll`) is used both by
`XxxRoutes.scala` (the live server) and `ApiSpec.allEndpoints` (OpenAPI generation) — they must
stay the exact same value, so `.securityIn(auth.bearer[String]())` is added directly to each
endpoint definition in `XxxEndpoints.scala`, changing its type from `PublicEndpoint[I, E, O, Any]`
to `Endpoint[String, I, E, O, Any]`. Each endpoint's existing `errorOut(oneOf[...])` gains
`EndpointErrors.unauthorizedVariant(...)` (already exists, added in step 1 for login's own 401,
reused here) so the 401 is documented per-endpoint in the generated spec.

**Rejected:** attaching security only in `XxxRoutes` and leaving `XxxEndpoints` (hence the
generated OpenAPI spec) looking public — would make the spec lie about what the API actually
requires.

### 2. A shared `SecuredEndpoint.securityLogic` helper, not copy-pasted per resource

One tiny helper in `adapter-http/.../common/SecuredEndpoint.scala`:

```scala
object SecuredEndpoint:
  def securityLogic(tokenService: TokenService)(token: String): IO[(StatusCode, HttpErrorResponse), String] =
    tokenService.validate(token).mapError(ErrorMapper.toHttpError)
```

Every `XxxRoutes` class calls `SomeEndpoint.zServerSecurityLogic(SecuredEndpoint.securityLogic(tokenService)).serverLogic { _ => args => ... }`
instead of today's single-phase `SomeEndpoint.zServerLogic { args => ... }`. The validated
username (the `_`) is discarded — nothing consumes it yet, since there's no per-user filtering or
role check in this step (that's step 3/4). Business logic bodies are otherwise **completely
unchanged** — this is purely a wrapping change, not a rewrite of any use-case call.

### 3. Every `XxxRoutes` class gains a `tokenService: TokenService` constructor parameter

Same pattern as every other dependency (constructor-injected, not read from the ZIO environment)
— consistent with how `AirlineRoutes` already takes six use-case interfaces as constructor
params. `WiringModule` merges the existing `jwtServiceLayer` (already built for `authUseCaseLayers`
in step 1) into each resource's use-case layers before wiring into `XxxRoutes.layer`.

### 4. `Health` and `Auth` (login) are explicitly untouched

`HealthEndpoints`/`HealthRoutes` and `AuthEndpoints`/`AuthRoutes` get no changes at all — they
must keep working with no token (health probes; and login is how you obtain a token to begin
with — gating it would be a chicken-and-egg lockout).

## Implementation

For **each** of Country, Airport, Airline, Aircraft, Flight, FlightInstance, Route:

- `adapter-http/.../<resource>/<Resource>Endpoints.scala` — add `.securityIn(auth.bearer[String]())`
  and `EndpointErrors.unauthorizedVariant("Missing or invalid token.")` to every endpoint `val`.
- `adapter-http/.../<resource>/<Resource>Routes.scala` — add `tokenService: TokenService`
  constructor param; wrap every `.zServerLogic { args => ... }` call into
  `.zServerSecurityLogic(SecuredEndpoint.securityLogic(tokenService)).serverLogic { _ => args => ... }`;
  update the companion `val layer` to require `TokenService` too.
- `bootstrap/.../WiringModule.scala` — merge `jwtServiceLayer` into each resource's use-case
  layers before `>>> XxxRoutes.layer`.
- `adapter-http/src/test/.../<resource>/<Resource>EndpointsSpec.scala` — `makeBackend` gains a
  `tokenService: TokenService` param (default: a stub that always succeeds, returning a fixed
  username); every existing request in the spec gains a valid `Authorization: Bearer test-token`
  header (via a shared test constant, not a real JWT — the stub `TokenService` never actually
  decodes it); new cases per resource: missing header → 401, stub `TokenService` failing → 401.

New shared file:

- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/common/SecuredEndpoint.scala`
  (decision 2).

## Tests

- One new small suite per resource's existing `EndpointsSpec` (not a new file — folded into the
  existing suite): "returns 401 when the Authorization header is missing", "returns 401 when the
  token is rejected by TokenService". Every pre-existing test in each file needs the bearer header
  added or it now fails with 401 instead of its expected status.
- No new integration-tests suite — `TokenService`/`JwtService` behavior itself was already fully
  covered in step 1 (`JwtServiceSpec`); this step is pure HTTP-layer wiring.

## Implementation notes (discovered while building, not anticipated in the design above)

- **Two distinct 401 body shapes, mirroring step 1's 400 discovery.** A *missing* Authorization
  header is rejected by Tapir's own decode-failure handling before `zServerSecurityLogic` ever
  runs — plain text body (`Invalid value for: header Authorization (missing)`), same mechanism as
  the schema-validator 400s from step 1. A *present-but-rejected* token goes through
  `SecuredEndpoint.securityLogic` → `TokenService.validate` → `ErrorMapper` — JSON body
  (`{"message":"Invalid token","errors":["Invalid token"]}`). Both return status 401, but a
  frontend/API consumer parsing the body as JSON unconditionally would break on the first case —
  worth documenting for whoever builds the next client-facing piece, same as the 400 case.
- **The Postman sync surfaced a real gap the automated pipeline doesn't cover.**
  `openapi-to-postmanv2` added `auth: {type: bearer, bearer: [{value: "{{bearerToken}}"}]}` to
  every *non-E2E* request (correctly reading the new `httpAuth` security scheme from the spec),
  but `sync.py`'s core design principle — E2E folders are preserved byte-for-byte across syncs,
  never touched — meant the 5 E2E folders' 36 requests kept their pre-existing `auth: null` and
  never got this wiring at all. Fixed by hand: added the same bearer-auth object directly to every
  E2E request, plus a `bearerToken` environment variable (`docs/api/environment.json`) that didn't
  exist before (the sync script only auto-propagates *path* variables like `{code}`/`{iata}`, not
  auth-scheme variables Postman's converter introduces).
- **A collection-level pre-request auto-login script was added but doesn't reliably populate the
  token in time under Newman** (`pm.sendRequest`'s callback, expected to block per Postman's own
  documented sandbox behavior, didn't complete before the very next request's headers were built,
  in this Newman version). Left in the collection as a best-effort convenience for GUI users (this
  pattern is standard and known to work in the Postman desktop app), but Newman-based verification
  (this rollout's own, and `run-e2e-tests`) must pass a real token explicitly:
  `--env-var "bearerToken=$(curl ... | jq -r .token)"`. Not chased further — root-causing Newman's
  sandbox timing was out of scope for what this step needed to prove.

## Verification performed

- `sbt scalafmtAll` (root + `integrationTests/scalafmtAll`) → `sbt clean compile` — zero
  errors/warnings.
- `sbt test` — 384 tests total (370 + 14 new: one "missing header" + one "token rejected" test
  per resource, ×7), all green.
- `sbt integrationTests/test` — 60 tests, unchanged and all green (this step is pure HTTP-layer
  wiring; `TokenService`/`JwtService` behavior was already fully covered in step 1).
- Live-verified against the real app (fresh `bootstrap/assembly`): every `/api/v1/*` endpoint
  across all seven resources returns 401 with no `Authorization` header and with a garbage token,
  and 200 with a real token obtained from `/api/v1/auth/login`; `/health/live`, `/health/ready`,
  and `/api/v1/auth/login` itself all still work with no token at all.
- `validate-openapi` skill: PASSED — Redocly 0 errors, 0 inline schemas, Spectral 0 errors. Spot
  checked the generated spec directly: `GET /api/v1/countries` declares `security: [{httpAuth:
  []}]` and a `401` response; `POST /api/v1/auth/login` declares `security: None` (still public).
- `sync-postman-collection` skill: synced (every non-E2E request gained bearer auth
  automatically); Newman run against the live app with a real token supplied via `--env-var` —
  45 requests, 0 request failures, 0 `401`s anywhere (confirming the auth wiring itself is
  correct). The 22 remaining assertion failures are a **pre-existing, unrelated** issue: this dev
  Postgres has the full 249-row ISO country seed loaded (`plans/seed-data-countries-airports.sql`),
  so the E2E fixtures' choice of real country codes as throwaway test data (`PT`/Portugal for the
  Country lifecycle, `KI`/Kiribati for the Airport/Airline/Aircraft dependency chain, `TV` for the
  search flow) collides with rows that already exist — true regardless of Auth, reproducible
  against any fully-seeded dev DB. Flagged to the user rather than silently reworked.
- `docs/api/endpoint-status.md`, `docs/todo/auth-jwt.md`'s roadmap, this doc's status header.
