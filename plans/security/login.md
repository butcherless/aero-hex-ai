# Plan: `POST /api/v1/auth/login` (First Security Step — Authentication Only)

> **Status:** Implemented and verified live against a real local Postgres. Step 1 of the security
> roadmap tracked in `docs/todo/auth-jwt.md`'s `## Roadmap` section.

## Goal

Add security to aero-hex-ai step by step. This is **step 1: authentication** — a user submits
credentials and receives a self-issued JWT. Nothing else changes: no existing endpoint is
protected yet, there is no public registration endpoint, and there is no role/permission model.
Those are follow-up steps (see `## Deliberately out of scope`).

This adapts `docs/todo/security/security-analysis-aero-hex-ai.md` §2 **Option A — Manual JWT**
to this project's actual module layout, error hierarchy, Tapir/Quill conventions, and config
style. A second, already-project-flavored doc exists at `docs/todo/auth-jwt.md` (status:
analysis, not implemented) that reaches similar conclusions independently — this plan
reconciles both rather than re-deriving from scratch, and supersedes them for the scope
below once implemented (keep both docs afterward as the historical rationale, per
`docs/CLAUDE.md`'s convention).

## Decisions

### 1. JWT library: `jwt-scala` / `jwt-circe`, not `zio-jwt-validator` or `zio-http-pac4j`

The security-analysis doc's own comparison table already points here: this app **issues its
own tokens** — there's no external IdP (Auth0/Keycloak) to validate against, which is the
whole reason `zio-jwt-validator` exists, and no need for OAuth2/OIDC/SAML/social login, which
is `zio-http-pac4j`'s reason to exist. Between `jwt-core` (used in the abstract doc's example)
and `jwt-circe`: this project already depends on Circe everywhere (`adapter-http`'s DTOs,
`messaging-kafka`'s event codec) — `jwt-circe` is Circe-native, so there's no separate JSON
bridge to write. Exact version TBD at implementation time — check current GA on Maven Central
per `## Versioning policy` (last checked, `auth-jwt.md` cited 10.0.1; not re-verified now).

**Rejected:** `zio-jwt-validator` (wrong direction — validates externally-issued tokens, this
project mints its own); `zio-http-pac4j` (heavyweight multi-protocol framework for a
single-service, self-issued-JWT use case).

**Note — this is deliberately not OAuth2.** `POST /api/v1/auth/login` is a custom bearer-token
scheme (username/password in, self-signed JWT out), not an RFC 6749 authorization server — no
grant types, no `/token` endpoint shape, no scopes/client credentials. Tapir's
`auth.oauth2.authorizationCodeFlow(...)` exists to *document* a real OAuth2 flow in the OpenAPI
spec, but implementing actual OAuth2 would need an authorization-server role (self-built or via
`zio-http-pac4j`/an external IdP) — out of scope here by explicit choice, confirmed with the
user. `auth.bearer[String]()` (decision 4) surfaces as a plain `bearer` HTTP security scheme in
the generated spec, not `oauth2`.

### 2. Password hashing: `jbcrypt`

No ZIO-ecosystem or stdlib alternative exists for password hashing (falls to the versioning
policy's third tier: third-party, only because nothing else clears the bar). `jbcrypt` is the
reference BCrypt implementation, tiny, no transitive deps.

### 3. New `infrastructure/security` module, not embedded in `application` or `adapter-http`

Every existing driven-port implementation (`port/out`) lives in its own infrastructure module —
`persistence-quill` implements repository ports, `messaging-kafka` implements the event
publisher port. `JwtService` and `PasswordHasher` implement a new `TokenService`/`PasswordHasher`
`port/out` the same way, so by that established convention they get their own sibling module,
depending only on `domain` (mirrors `persistenceQuill`/`messagingKafka` in `build.sbt`):

```scala
lazy val security = project
  .in(file("infrastructure/security"))
  .dependsOn(domain)
  .settings(
    name := "security",
    libraryDependencies ++= Seq(jwtScalaCirce, jbcrypt)
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)
```

Added to `coverageProjects`; `bootstrap` gains `security` in its `.dependsOn(...)`.

**Rejected:** putting `JwtService`/`PasswordHasher` directly in `adapter-http` — that module is
purely the *driving* side today (Tapir routes calling `port/in` use cases); it implements no
`port/out`, and adding one here breaks that boundary and forces a jwt/bcrypt dependency onto
the HTTP module for no reason. Rejected: `application` — same reason `CreateAirlineService`
doesn't itself talk to Postgres; orchestration belongs in `application`, the actual
crypto/library call belongs in infrastructure.

### 4. Defer "HandlerAspect vs. Tapir security phase" — not needed for step 1

The abstract doc's Option A example uses a raw `zio-http` `HandlerAspect` to *validate an
incoming* token before a handler runs. That question doesn't arise yet: `/auth/login` is a
public endpoint (no incoming token to check), so step 1 only needs **token issuance**, not
validation. Flagging now because it matters for the *next* step (protecting existing
endpoints): 100% of this project's routes are Tapir-defined, converted via `.zServerLogic` and
served through one `ZioHttpInterpreter().toHttp(...)` call, and `## REST API` in the root
CLAUDE.md is explicit that Tapir is the *only* source of truth for the OpenAPI spec. A raw
`HandlerAspect` wrapped around the final interpreted `Routes[Any, Response]` would validate
tokens entirely outside Tapir — the 401 it produces would never appear in the generated spec.
`docs/todo/auth-jwt.md` §4.3 already sketched the alternative (Tapir's `securityIn` +
`zServerSecurityLogic` two-phase mechanism, the same "phase 1 resolves identity, phase 2 runs
business logic" idea as `HandlerAspect`, just expressed through Tapir so the 401 stays part of
the contract). **Recommendation for the next step:** adapt Option A's manual-JWT-decode logic
into that Tapir security phase rather than a bare `HandlerAspect`. Not a decision this task
needs to make.

### 5. `users` table: no roles column yet, no registration endpoint yet

Coarse-grained/role-based authorization is explicitly the *next* security step (the abstract
doc's §3, `auth-jwt.md` §6) — adding a `roles` array now would be speculative. The table stores
just enough to authenticate: `id`, `username`, `password_hash`, `created_at`.

Since there's no registration endpoint in this step, there also needs to be a way to have a
user to log in with. Following this project's **existing** precedent for one-off dev data
(`plans/seed-data-countries-airports.sql`, `plans/seed-data-airlines.sql`,
`plans/seed-data-aircraft.sql` — manual `psql`-loaded scripts, deliberately **not** Flyway
migrations), add `plans/seed-data-users.sql` with one dev user and a precomputed bcrypt hash,
loaded the same way (`docker exec -i ... psql ... < plans/seed-data-users.sql`). The Flyway
migration itself (`V16`) only creates the schema.

### 6. Error handling: generic message for both "no such user" and "wrong password"

Pulled directly from `auth-jwt.md` §7 (already-considered, sound): distinguishing "unknown
user" from "wrong password" in the response enables user enumeration. `ErrorMapper` maps both
new `DomainError.UserNotFound` and `DomainError.InvalidCredentials` to the same
`401 Unauthorized` / `"Invalid credentials"` body.

### 7. Config: `jwt.secret-key` / `jwt.ttl-seconds`, both env-overridable from day one

Mirrors `application.conf`'s existing pattern exactly (default literal, then reassigned to
`${?ENV_VAR}`). Called out because the *existing* `kafka.group-id` setting is on record
(`## Pending implementations` in root CLAUDE.md) as having been added **without** its
override — a known gap to not repeat here:

```
jwt {
  secret-key = "dev-only-change-me"
  secret-key = ${?JWT_SECRET_KEY}
  ttl-seconds = 3600
  ttl-seconds = ${?JWT_TTL_SECONDS}
  issuer = "aero-hex-ai"
  issuer = ${?JWT_ISSUER}
  audience = "aero-hex-ai-api"
  audience = ${?JWT_AUDIENCE}
}
```

### 8. JWT claims: RFC 7519 registered claims, no private claims yet

Confirmed against `jwt-scala`'s own `JwtClaim` API (`.by`/`.to`/`.about`/`.withId`/`.expiresIn`/
`.startsNow`/`.issuedNow`) — all seven registered claims are populated on every issued token
instead of stuffing an ad-hoc payload:

| Claim | Set via | Value |
|---|---|---|
| `iss` | `.by(...)` | `jwt.issuer` config |
| `sub` | `.about(...)` | `username` — this project's established natural-key-as-domain-identity convention (see below), not the surrogate `id` |
| `aud` | `.to(...)` | `jwt.audience` config |
| `exp` | `.expiresIn(ttlSeconds)` | `jwt.ttl-seconds` config |
| `nbf` | `.startsNow` | token not valid before its own issuance |
| `iat` | `.issuedNow` | jwt-scala's own README calls this a security recommendation, not optional |
| `jti` | `.withId(UUID.randomUUID().toString)` | unique per-token id — no revocation list yet, but avoids a payload-shape change if one is added later |

No private/custom claims (e.g. roles) yet — nothing in step 1 consumes the decoded token, so
there's nothing to justify embedding beyond the registered set. `TokenService.generate`
therefore only needs `username: String` (see below) — **not** the surrogate id: every existing
domain entity (`Country(code, name)`, `Airline(icaoCode, ...)`) deliberately excludes the
surrogate `BIGINT id` per `CLAUDE.md`'s `## Database schema` ("the surrogate id is
persistence-only, domain/ports never see it") — the domain-facing identity is always the
natural key. `User`'s natural key is `username` (already `UNIQUE` in the `users` table), so it
follows the same rule and is what goes in `sub`, RFC 7519 doesn't require `sub` to be numeric,
just a stable string identifying the principal within the issuer's context, which `username`
already satisfies. (An earlier draft of this doc used the numeric id here — inconsistent with
the rest of the codebase; fixed.)

**Correction — `TokenService.validate` belongs in step 1 too, just unwired from HTTP.**
"The token expires" is only a provable claim if something actually decodes it and confirms an
expired token is rejected — `generate` alone can't demonstrate that. So `TokenService` gets a
second method now: `def validate(token: String): IO[DomainError, String]` (returns the `sub`,
i.e. `username`), implemented by `JwtService` via `Jwt.decode(token, secretKey, Seq(HS256))`,
which auto-checks `exp`/`nbf` and fails a `Try` on either. This is **not** the same thing as
"step 2" (decision 4) — decision 4 is specifically about *wiring token validation into an HTTP
endpoint's security phase* (`auth.bearer` + `zServerSecurityLogic`), which genuinely has no
consumer yet since no endpoint is protected. `validate` existing and being unit-tested inside
`infrastructure/security` doesn't require any endpoint to call it — it's how `JwtServiceSpec`
proves expiry works with a fixed `Clock`, entirely internal to this step.

One gap to flag for later, not blocking now: `Jwt.decode` auto-checks `exp`/`nbf`, but **not**
`iss`/`aud` — those need an explicit `claim.isValid(issuer, audience)` check (or equivalent)
once `validate` has an actual caller in step 2, otherwise a token minted for a different
issuer/audience would still pass. `validate`'s step-1 implementation can do this check now too
(cheap, no reason to wait), it just has no HTTP caller to exercise it against yet.

### 9. Signing algorithm: stay on HS256 (symmetric) for now

`auth-jwt.md` §7 already answered the question "when do we need RS256 instead" —
**"switch to RS256 if multiple independent services need to verify tokens."** That condition
isn't true today: there is exactly one verifier (this same process). Switching preemptively
would mean managing an RSA/EC keypair (generation, storage, rotation) instead of one config
string, for zero present benefit.

**Why this doesn't compromise the "future microservice" goal:** the `TokenService` port
(`generate(username)`/`validate(token)`) doesn't change shape at all when the algorithm changes
— `JwtAlgorithm.HS256` → `RS256`/`ES256` and `JwtConfig.secretKey` → a keypair are both confined
entirely to `JwtService`'s internals in `infrastructure/security`. Nothing in `domain`,
`application`, or `adapter-http` would need to change. That's the concrete payoff of decision 3
(giving `TokenService` its own port + its own infra module in the first place): the algorithm
choice is swappable later, at the point it's actually needed, without touching the rest of the
system. Flip the switch when a second, independent service needs to verify tokens without
holding the same shared secret Auth uses to sign them.

## Future: extracting Auth into its own service (not this task)

Prompted by a direct question: is this design separated cleanly enough from business logic to
enhance independently or move to its own microservice later? Yes, by construction — the
relevant properties are already decisions made above, not new ones:

- **No cross-imports.** `domain/user`, `application/auth`, `adapter-http/auth` reference nothing
  in `country`/`airport`/`airline`/`aircraft`/`route`/`flight`, and (until step 2) nothing
  references them either. Step 2's protected endpoints will depend only on the narrow
  `TokenService.validate` port — never on `LoginService`/`UserRepository`/`JwtService`
  internals (decision 3).
- **Independent schema.** `users` has no FK to or from any other table (decision 5) — it can be
  moved to its own database/schema without touching a migration anywhere else.
- **Isolated dependency footprint.** Only `infrastructure/security` depends on `jwt-scala`/
  `jbcrypt` (decision 3) — extracting it doesn't drag those libraries into whatever's left.
- **Stateless, locally-verifiable tokens.** Because `exp`/`nbf`/`iss`/`aud` are all
  self-contained in the token (decision 8), a future resource server never needs to call back
  to an "auth service" synchronously to check a token — it verifies locally with a shared
  secret (today) or a public key (after decision 9's HS256→RS256 flip), which is precisely what
  keeps a microservice split cheap instead of trading one kind of coupling for another
  (a hard network dependency on Auth for every single request).

**What's deliberately not done, and why it's still the right call:** the sbt module graph stays
organized by hexagonal layer, not by feature — `plans/package-by-feature-refactor.md` already
settled this for the whole project ("collapsing into per-feature modules... would defeat that
purpose and is out of scope"), so `auth-domain`/`auth-application`/`auth-adapter-http` modules
are not on the table; that would reverse a decision already made project-wide, not something
specific to Auth. The actual extraction lever, when genuinely wanted, is a separate deployable
— this repo already has the exact precedent in `master-data-sync`: its own `Main`, its own sbt
module, depending on `domain`/`application`/`persistence-quill` (Auth's equivalent would add
`infrastructure/security`), deliberately outside `bootstrap`'s `WiringModule` and root's
aggregate. Not built now — it would mean running two local processes for a single login
endpoint, which is premature for a one-endpoint feature — but it's the well-defined next step
if/when actual extraction is pursued.

## Implementation

- **`domain/src/main/scala/dev/cmartin/aerohex/domain/user/`**
  - `User.scala` — `case class User(username: String, passwordHash: String)` (no surrogate id,
    matching `Country`/`Airline`'s convention — plain, like `Airline`/`Aircraft` — no
    opaque-type wrapper needed for `username`, it's never parsed/validated beyond non-blank at
    the DTO layer).
  - `UserRepository.scala` (`port/out`-style, same flat convention as e.g.
    `AirlineRepository`) — `def findByUsername(username: String): IO[DomainError, User]`.
  - `TokenService.scala` — driven port: `def generate(username: String): UIO[String]` (an
    opaque `Jwt` value or a plain `String` — plain `String` matches this project's low ceremony
    for DTO-adjacent values; claim shape is decision 8) and `def validate(token: String):
    IO[DomainError, String]` (returns the `sub`/`username`; not called from any endpoint yet —
    exists so expiry is actually provable in step 1, see the correction above).
  - `PasswordHasher.scala` — driven port: `def verify(plain: String, hash: String): UIO[Boolean]`.
  - `error/DomainError.scala` — add `UserNotFound(username: String)`, `InvalidCredentials`,
    `InvalidToken(reason: String)`, `TokenExpired` (for `validate`'s failure cases).
- **`infrastructure/security/src/main/scala/dev/cmartin/aerohex/infrastructure/security/`**
  - `JwtService.scala` — implements `TokenService` via `jwt-circe`. `generate` builds the claim
    per decision 8 (`JwtClaim().by(issuer).to(audience).about(username).withId(uuid)
    .issuedNow.startsNow.expiresIn(ttlSeconds)`). `validate` calls `JwtCirce.decode(token,
    secretKey, Seq(JwtAlgorithm.HS256))` (auto-checks `exp`/`nbf`), then additionally checks
    `claim.isValid(issuer, audience)` for `iss`/`aud` (not automatic — see the note above),
    mapping any failure to `DomainError.TokenExpired`/`InvalidToken`; companion `val layer`
    (`URLayer[JwtConfig, TokenService]`, config supplied via `ZLayer.fromFunction`, wired from
    `application.conf` the same way other `bootstrap`-read config sections are).
  - `JwtConfig.scala` — `case class JwtConfig(secretKey: String, ttlSeconds: Int, issuer: String, audience: String)`.
  - `BcryptPasswordHasher.scala` — implements `PasswordHasher` via `jbcrypt`; `val layer`.
- **`infrastructure/persistence-quill/src/main/scala/dev/cmartin/aerohex/infrastructure/persistence/quill/user/`**
  - `QuillUserRepository.scala` — same shape as `QuillAirlineRepository`: private `UserRow`
    carries the DB's surrogate `id` (present for consistency with every other table, even
    though nothing FKs to `users` yet), `querySchema[UserRow]("users")`,
    `.filter(_.username == lift(username))`, zero-rows refined to `DomainError.UserNotFound`
    via the existing `QuillSqlState.refineZeroRows` helper; the row-to-domain mapping drops
    `id`, same as `QuillCountryRepository` mapping `CountryRow(id, code, name)` down to
    `Country(code, name)`; companion `val layer: URLayer[DataSource, UserRepository]`.
- **`application/src/main/scala/dev/cmartin/aerohex/application/auth/`**
  - `LoginUseCase.scala` (`port/in`) — `def login(username: String, password: String): IO[DomainError, String]`.
  - `LoginService.scala` — implements it: `userRepo.findByUsername` → `passwordHasher.verify` →
    on success `tokenService.generate`, on failure `ZIO.fail(DomainError.InvalidCredentials)`
    (both the not-found and wrong-password paths collapse here, per decision 6); `val layer`
    composed from `UserRepository & PasswordHasher & TokenService`.
- **`adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/auth/`**
  - `AuthDto.scala` — `LoginRequest(username: String, password: String)`,
    `TokenResponse(token: String, tokenType: "Bearer", expiresIn: Int)`.
  - `AuthEndpoints.scala` — `endpoint.post.in("api"/"v1"/"auth"/"login").in(jsonBody[LoginRequest])`,
    `oneOf` error output with `EndpointErrors`'s existing `badRequestVariant` +
    a new/reused `401` variant + `unexpectedError` (matching `CountryEndpoints`'s shape).
  - `AuthRoutes.scala` — `serverEndpoints: List[ZServerEndpoint[Any, Any]]` via
    `AuthEndpoints.login.zServerLogic { req => loginUseCase.login(req.username, req.password).map(TokenResponse.from).mapError(ErrorMapper.toHttpError) }`;
    companion `val layer: URLayer[LoginUseCase, AuthRoutes]`.
  - `error/ErrorMapper.scala` — add the `UserNotFound`/`InvalidCredentials` → `401` case, plus
    `TokenExpired`/`InvalidToken` → `401` (unreachable via HTTP in step 1 — no endpoint calls
    `validate` yet — but required regardless: `ErrorMapper`'s `match` over the sealed
    `DomainError` must stay exhaustive once those two cases exist, or `compile` fails).
  - `ApiSpec.scala` — append `AuthEndpoints.login` to `allEndpoints`.
- **`bootstrap/src/main/scala/dev/cmartin/aerohex/bootstrap/WiringModule.scala`**
  - `userRepoLayer = QuillDataSourceLayer.live >>> QuillUserRepository.layer`
  - `jwtConfigLayer` read from `application.conf` (mirrors how other config-backed layers are
    built), `>>> JwtService.layer`; `passwordHasherLayer = ZLayer.succeed(BcryptPasswordHasher())`... `>>> BcryptPasswordHasher.layer`
  - `authUseCaseLayers = (userRepoLayer ++ jwtServiceLayer ++ passwordHasherLayer) >>> LoginService.layer`
  - folded into `appLayer` the same way every other resource is: `(authUseCaseLayers >>> AuthRoutes.layer) ++ ...`
- **`infrastructure/migration/src/main/resources/db/migration/V16__create_users.sql`**
  ```sql
  CREATE TABLE users
  (
      id            BIGSERIAL PRIMARY KEY,
      username      VARCHAR(100) NOT NULL UNIQUE,
      password_hash CHAR(60)     NOT NULL,
      created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
  );
  ```
  (surrogate `BIGSERIAL id`, matching every other table's convention per `## Database schema`).
- **`plans/seed-data-users.sql`** — one dev user, precomputed bcrypt hash, loaded manually
  (decision 5).
- **`bootstrap/src/main/resources/application.conf`** — new `jwt { ... }` block (decision 7).
- **`project/Versions.scala` / `project/Dependencies.scala`** — `jwtScalaCirce`, `jbcrypt`
  version + module entries.
- **`build.sbt`** — new `security` project (decision 3), `bootstrap.dependsOn(..., security)`,
  `security` added to `coverageProjects`.

## Tests

- `infrastructure/security/src/test/.../JwtServiceSpec.scala` — inject a fixed `Clock`
  (`auth-jwt.md` §7's own recommendation) for deterministic expiry assertions; round-trip
  generate→decode.
- `infrastructure/security/src/test/.../BcryptPasswordHasherSpec.scala` — verify a known
  hash matches, a wrong password doesn't.
- `application/src/test/.../auth/LoginServiceSpec.scala` — stub `UserRepository`/
  `PasswordHasher`/`TokenService`, same style as `AirlineServiceSpec`; cases: correct
  credentials → token, unknown username → `InvalidCredentials`, wrong password →
  `InvalidCredentials` (same error both times, per decision 6).
- `adapter-http/src/test/.../auth/AuthEndpointsSpec.scala` — `TapirStubInterpreter` +
  `sttp-client4`, same pattern as `CountryEndpointsSpec`/`AirlineEndpointsSpec`: 200 + token
  body on success, 401 on bad credentials, 400 on malformed request body.
- `infrastructure/integration-tests/.../support/UserRepositoryContractSpec.scala` — real
  Postgres via Testcontainers: `findByUsername` hits the seeded row (or a test-inserted one),
  not-found path returns `UserNotFound`.

## Implementation notes (discovered while building, not anticipated in the design above)

- **`UserRepository.findByUsername` returns `Option[User]`, not a `NotFound` error.** Checking
  `QuillAirlineRepository`/`FindAirlineService` mid-implementation showed every `findByX` in this
  codebase returns `Option`, with `NotFound` reserved for update/delete (zero-rows-affected). The
  application layer unwraps it with `.someOrFail(...)` — here, straight to
  `DomainError.InvalidCredentials` (decision 6), so a distinct `UserNotFound` case was never
  actually needed and wasn't added to `DomainError`.
- **`AccessToken(value, expiresInSeconds)` value object, not a bare `String`.** The original draft
  had `TokenService.generate`/`LoginUseCase.login` return a plain token string, but
  `TokenResponse.expiresIn` needs the TTL too, and nothing upstream of `JwtService` should know
  about `JwtConfig`. Bundling both in one small domain-level value (returned all the way up
  through `LoginUseCase` to `AuthRoutes`) avoided leaking config across layers or re-decoding a
  token immediately after minting it just to learn its own expiry.
- **`application.conf` is documentation, not live config.** Nothing in this codebase actually
  parses the HOCON file — `KafkaConfig`/`QuillDataSourceLayer` both read `sys.env.getOrElse(...)`
  directly in Scala, and `application.conf`'s existing `postgres`/`kafka`/`http` blocks are a
  human-readable mirror of those same env vars, never loaded by any config library. `JwtConfig`
  follows the same real pattern (`sys.env.getOrElse` in `JwtConfig.default`); the `jwt {}` block
  added to `application.conf` is documentation-only, consistent with the rest of the file.
- **`TokenService.validate` avoids jwt-scala's internal exception hierarchy.** Rather than guess
  at `pdi.jwt.exceptions` class names to distinguish "expired" from "otherwise invalid", `JwtService`
  decodes with `JwtOptions(expiration = false, notBefore = false)` (signature still verified) and
  then checks `exp`/`nbf`/`iss`/`aud` manually against the injected `Clock` — fully self-contained,
  and exactly what `JwtServiceSpec`'s fixed-clock tests exercise.
- **New `EndpointErrors.unauthorizedVariant`.** No 401 variant existed before this feature (nothing
  needed one); added alongside `badRequestVariant`/`notFoundVariant`/`conflictVariant` so the login
  endpoint's 401 is documented in the OpenAPI spec rather than falling through to the generic
  `unexpectedError` default variant.
- **`ErrorMapper`'s `match` needed all three new `DomainError` cases**, including `InvalidToken`/
  `TokenExpired`, which no endpoint can trigger yet (`validate` has no HTTP caller in this step) —
  otherwise the match over the sealed `DomainError` isn't exhaustive and `compile` fails.
- **`HttpServer.AppRoutes`/`serve` needed updating too**, not just `WiringModule` — the type alias
  and the `business` endpoint list live in `adapter-http`'s `HttpServer.scala`, a file the original
  plan didn't call out explicitly.
- **`users.password_hash CHAR(60)` padding bit the integration test, not production.** Postgres
  space-pads a `CHAR(60)` value shorter than 60 characters on read; a real bcrypt hash is always
  exactly 60 characters (this is *why* `CHAR(60)` rather than `VARCHAR` was chosen), so this only
  surfaced because the contract test's placeholder hash was shorter — fixed by using a realistic
  60-character fake hash in the test, not a schema change.
- **The seed hash was generated via `jbcrypt` itself** (`sbt security/console`,
  `BCrypt.hashpw("ChangeMe123!", BCrypt.gensalt(10))`), not a different bcrypt implementation
  (e.g. Apache's `htpasswd -bnB`, which emits a `$2y$` prefix) — guarantees
  `BcryptPasswordHasher.verify` accepts it.
- **`FlywayMigrationItSpec`'s hardcoded `version == "15"` assertion** needed bumping to `"16"` —
  an expected, mechanical consequence of adding a migration, not a design decision.

## After implementation

- `sbt scalafmtAll` → `sbt compile` (zero errors/warnings) → `sbt test` → `sbt integrationTests/test`
  — all green (see `## Verification performed`).
- `docs/api/endpoint-status.md` — added the `Auth | POST | /api/v1/auth/login` row.
- `validate-openapi` then `sync-postman-collection` skills — both run, both clean (see below).
- Manually verified: seeded the dev user via `plans/seed-data-users.sql`, `curl -X POST
  .../api/v1/auth/login` with correct/incorrect/unknown-username credentials and a blank-field
  body, decoded the returned JWT to confirm all seven registered claims and the exact 3600s
  `exp`-`iat` gap.
- Root `CLAUDE.md`: added `security` to the module dependency graph diagram and
  `## Hexagonal layer conventions`, and `domain/user/` to the domain layer's package list; updated
  the unit/integration test-count mentions (354→370, 58→60).
  `infrastructure/integration-tests/CLAUDE.md` updated the same way (V15→V16, 58→60, added User).

## Verification performed

- `sbt scalafmtAll` (root + `integrationTests/scalafmtAll`) → `sbt clean compile` — zero
  errors/warnings.
- `sbt test` — 370 tests total (354 + 16 new: 7 in the new `security` module
  (`JwtServiceSpec`/`BcryptPasswordHasherSpec`), 4 in `application` (`LoginServiceSpec`), 5 in
  `adapter-http` (`AuthEndpointsSpec`)), all green.
- `sbt integrationTests/test` — 60 tests total (58 + 2 new `QuillUserRepositoryItSpec` tests), all
  green, real Postgres via Testcontainers.
- Live-verified against the real app (fresh `bootstrap/assembly`, Flyway auto-applied `V16` on
  startup): valid credentials → 200 + JWT; wrong password → 401 `"Invalid credentials"`; unknown
  username → the *same* 401 `"Invalid credentials"` (confirming decision 6 — no enumeration
  oracle); blank username → 400 via Tapir's own schema validator. Decoded the JWT payload:
  `{"iss":"aero-hex-ai","sub":"admin","aud":"aero-hex-ai-api","exp":...,"nbf":...,"iat":...,"jti":"..."}`
  — `exp - iat == 3600`, matching `jwt.ttl-seconds`.
- `validate-openapi` skill: PASSED — Redocly 0 errors, 0 inline schemas, Spectral 0 errors;
  `Auth | POST | /api/v1/auth/login` correctly listed in the endpoint inventory.
- `sync-postman-collection` skill: synced cleanly — one new `Auth` folder added (nothing removed,
  nothing orphaned), `environment.json` unchanged (no new path variables). Newman's E2E run hit
  pre-existing failures unrelated to this change (a stray `"KI"`/`"Kiribati"` row already in the
  dev DB, outside the official seed script, colliding with the Country/Airport/Airline/Aircraft
  E2E folders' own test fixture — Auth touches none of those entities and has no E2E folder of its
  own yet); flagged to the user rather than silently modified, since it's pre-existing dev-DB state
  unrelated to this feature.

## Deliberately out of scope (future steps, not this task)

- Public registration/create-user endpoint.
- Protecting any existing endpoint with the issued token (the `HandlerAspect`-vs-Tapir-security-
  phase decision from decision 4).
- Roles / RBAC (abstract doc §3 and §6, `auth-jwt.md` §6).
- `GET /api/v1/auth/me`.
- Refresh tokens.
