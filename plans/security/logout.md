# Plan: Server-side logout via token revocation (Step 3)

> **Status:** Implemented and verified live. Step 3 of the security roadmap tracked in
> `docs/todo/auth-jwt.md`'s `## Roadmap` section, building on steps 1–2
> (`plans/security/login.md`, `plans/security/protect-endpoints.md`).

## Goal

Add `POST /api/v1/auth/logout` — a protected endpoint that revokes the *specific* token used to
call it, so it can no longer authenticate anything even before its natural expiry. Until now,
"logout" was explicitly client-side-only (discard the token, redirect to login) because JWTs here
are stateless and the server held no session to invalidate. This closes that gap for the one case
that actually matters (a user deliberately signing out), without turning the whole scheme into a
stateful session store — only *revoked* tokens are tracked, not every issued one.

## Decisions

### 1. Revocation keyed by `jti`, stored in Postgres — not in-memory

Every token already carries a unique `jti` (added in step 1 specifically so a revocation list
could be bolted on later without changing the token shape). A `revoked_tokens` table
(`jti UUID PRIMARY KEY, expires_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ`) is the natural fit,
matching this project's existing persistence conventions (everything durable goes through
Postgres via Quill — see `WiringModule`'s header policy comment). `expires_at` mirrors the
*token's own* `exp` claim, not "now + something" — once that passes, the row is inert (the token
would be rejected as expired anyway), so it's kept only for a future cleanup query, not checked at
validation time. **Rejected:** an in-memory revocation set — wouldn't survive an app restart (a
revoked token would silently become valid again) and wouldn't work across multiple app instances;
inconsistent with every other piece of durable state in this project.

### 2. `TokenService.validate` now checks revocation, and returns a richer principal

`validate`'s signature changes from `IO[DomainError, String]` (bare username) to
`IO[DomainError, ValidatedToken]`, where `ValidatedToken(username: String, jti: String, expiresAt:
Instant)` — a small value object (same shape as step 1's `AccessToken`). Revocation-checking lives
*inside* `TokenService`/`JwtService`, not as a separate step composed at the call site: `JwtService`
gains a `RevokedTokenRepository` (new `domain` port, Quill-implemented) constructor dependency,
consulted as part of `validate`. This keeps "is this token usable" a single question with one
answer, asked identically by every protected endpoint via the existing `SecuredEndpoint` helper —
no resource's `Routes` class needs to change, since all six already discard the security phase's
resolved value (`.serverLogic { _ => args => ... }`); only `AuthRoutes`'s new `logout` handler
actually uses the richer `ValidatedToken.jti`/`.expiresAt`.

**Rejected:** checking revocation as a second, separately-composed step in
`SecuredEndpoint.securityLogic` (adapter-http) — would leak `jti` up into a layer that has no
other reason to know about JWT internals, and would mean two different ports being consulted for
one conceptual question ("can this token authenticate right now?").

### 3. `LogoutUseCase`/`LogoutService`, matching every other mutating action's shape

Even though the underlying action is a single delegating call (`tokenService.revoke(...)`), every
other mutation in this codebase — including one-liners like `DeleteCountryService` — goes through
a thin `application` service, never called directly from `adapter-http`. `LogoutService` follows
that same shape for consistency, despite having no real orchestration logic of its own.
`TokenService` itself stays a direct constructor dependency of `AuthRoutes` (as it already is,
implicitly, for `SecuredEndpoint`'s security-phase wrapping) — that part is HTTP-layer plumbing,
not a business use case, and doesn't change.

### 4. `logout` is itself a protected endpoint

You can only revoke the token you're currently presenting — there's no "log out someone else" or
"log out by username" capability, and none is needed. This also means `logout` goes through the
exact same `SecuredEndpoint` wrapper as every other protected endpoint, not a bespoke mechanism.

### 5. New `DomainError.TokenRevoked`, mapped to 401 like every other token failure

Distinguishing "revoked" from "expired"/"otherwise invalid" costs nothing (one more case, one more
`ErrorMapper` line) and gives a marginally clearer signal for logs/debugging, consistent with
already distinguishing `TokenExpired` from `InvalidToken` in step 1.

## Implementation

- **`domain/src/main/scala/dev/cmartin/aerohex/domain/user/`**
  - `TokenService.scala` — `validate` return type changes to `IO[DomainError, ValidatedToken]`;
    add `ValidatedToken(username: String, jti: String, expiresAt: Instant)`; add
    `def revoke(jti: String, expiresAt: Instant): UIO[Unit]`.
  - `RevokedTokenRepository.scala` (new) — `def isRevoked(jti: String): UIO[Boolean]`,
    `def revoke(jti: String, expiresAt: Instant): UIO[Unit]`.
  - `LogoutUseCase.scala` (new) — `def logout(jti: String, expiresAt: Instant): UIO[Unit]`.
  - `error/DomainError.scala` — add `case object TokenRevoked`.
- **`infrastructure/security/.../JwtService.scala`** — constructor gains
  `revokedTokenRepo: RevokedTokenRepository`; `validate` now also checks
  `revokedTokenRepo.isRevoked(jti)` (fails `TokenRevoked` if true) and returns `ValidatedToken`
  instead of a bare username; `revoke` delegates straight to
  `revokedTokenRepo.revoke(jti, expiresAt)`.
- **`infrastructure/persistence-quill/.../user/QuillRevokedTokenRepository.scala`** (new) — same
  shape as `QuillUserRepository`; `RevokedTokenRow(jti: UUID, expiresAt: Instant)`; `isRevoked`
  via `nonEmpty` on a filtered query, `revoke` via insert (idempotent — a duplicate revoke of the
  same `jti` is harmless, ON CONFLICT DO NOTHING at the SQL level).
- **`application/src/main/scala/dev/cmartin/aerohex/application/auth/LogoutService.scala`** (new)
  — implements `LogoutUseCase`, delegates to `tokenService.revoke`, `ServiceAspect.logged`-wrapped.
- **`adapter-http/.../auth/`**
  - `AuthEndpoints.scala` — add `logout: Endpoint[String, Unit, (StatusCode, HttpErrorResponse),
    Unit, Any]` — `base.post.securityIn(auth.bearer[String]()).in("logout")`, `204 No Content`,
    `errorOut` with `unauthorizedVariant` + `unexpectedError`.
  - `AuthRoutes.scala` — constructor gains `logoutUseCase: LogoutUseCase` and (now actually used)
    `tokenService: TokenService`; `logout` wired via
    `AuthEndpoints.logout.zServerSecurityLogic(secured).serverLogic { validated => _ =>
    logoutUseCase.logout(validated.jti, validated.expiresAt) }`.
  - `error/ErrorMapper.scala` — add `TokenRevoked` → `401`.
- **`bootstrap/.../WiringModule.scala`** — new `revokedTokenRepoLayer`; `jwtServiceLayer` becomes
  `(ZLayer.succeed(JwtConfig.default) ++ revokedTokenRepoLayer) >>> JwtService.layer`;
  `authUseCaseLayers` gains `jwtServiceLayer >>> LogoutService.layer`, combined with the existing
  login layer; `AuthRoutes.layer`'s required environment grows to include `LogoutUseCase`.
- **`infrastructure/migration/.../V17__create_revoked_tokens.sql`** — the table from decision 1.

## Tests

- `JwtServiceSpec` — existing tests updated for `validate`'s new `ValidatedToken` return type
  (constructed with a fake in-memory `RevokedTokenRepository`); new cases: "validate fails with
  TokenRevoked after revoke() is called for that jti", "revoke is idempotent (calling it twice
  doesn't error)".
- `application/.../auth/LogoutServiceSpec.scala` (new) — stub `TokenService`, assert `logout`
  delegates to `revoke` with the exact `jti`/`expiresAt` passed in.
- `adapter-http/.../auth/AuthEndpointsSpec.scala` — new `POST /api/v1/auth/logout` suite: 204 on
  a valid token, 401 on missing/rejected token (same stub-`TokenService` pattern as every other
  resource's `Authentication` suite from step 2).
- `infrastructure/integration-tests/.../support/RevokedTokenRepositoryContractSpec.scala` +
  `QuillRevokedTokenRepositoryItSpec.scala` (new) — `isRevoked` false before revoke, true after;
  revoking twice doesn't error.

## After implementation

- `sbt scalafmtAll` → `sbt compile` (zero warnings) → `sbt test` → `sbt integrationTests/test`.
- Manually verify: login → call a protected endpoint (200) → logout → same token on the same
  protected endpoint now returns 401 `TokenRevoked` → a fresh login still works.
- `validate-openapi` / `sync-postman-collection` — add the `Auth | POST | /api/v1/auth/logout`
  row to `docs/api/endpoint-status.md`; the Postman sync will pick up `logout` automatically
  (non-E2E, generated from the spec) — no E2E folder covers login/logout today, so no by-hand
  Postman fix is anticipated this time (unlike step 2's).
- `docs/todo/auth-jwt.md`'s roadmap, this doc's status header, root `CLAUDE.md` test counts.

## Implementation notes

Landed exactly as planned above, with one incidental fix: `adapter-http`'s
`common/SecuredEndpoint.scala` still declared its security-phase return type as
`IO[(StatusCode, HttpErrorResponse), String]` (from step 2, when `TokenService.validate` returned a
bare username). Once `validate` started returning `ValidatedToken`, this became a compile error at
`AuthRoutes.scala`'s new `logout` wiring (`value jti is not a member of String`). Fixed by updating
`SecuredEndpoint`'s signature to return `ValidatedToken` — source-compatible with all 6 other
resources' `Routes` classes, since they already discard the security-phase value via `_ =>`.

## Verification performed

- `sbt scalafmtAll` → `sbt compile` (zero warnings) → `sbt test` (391 unit tests) →
  `integrationTests/test` (63 tests, real Postgres via Testcontainers) — all green.
- Manual curl cycle against the live app: login → call a protected endpoint (200) → logout (204) →
  same token on the same protected endpoint now returns 401 with `TokenRevoked` → fresh login still
  works. Revocation confirmed persisted in Postgres via direct SQL query against `revoked_tokens`.
- `validate-openapi` skill — PASSED clean (Redocly 0 errors, 0 inline schemas, Spectral 0 errors),
  `logout` endpoint correctly present, 43 total endpoints.
- `sync-postman-collection` skill — added `POST /api/v1/auth/logout` to the Auth folder with
  bearer auth automatically wired (Login stayed public, as expected); noise-check confirmed the
  `collection.json` diff is a genuine structural change, `environment.json` unchanged.
- Newman against all 5 `E2E — ...` folders with a freshly-fetched bearer token: 0 request failures,
  0 auth-related assertion failures. The 22 assertion failures observed are the pre-existing,
  unrelated country-seed-collision pattern (E2E cleanup pre-request scripts issue a raw
  `pm.sendRequest` DELETE with no Authorization header, so cleanup silently 401s and leftover
  `PT`/`KI` codes cause downstream 409 conflicts on re-run) — not a regression from this feature.

## Deliberately out of scope (unchanged from earlier steps)

- Refresh tokens, `/auth/me`, registration, roles/RBAC — none of this step's concern.
- Bulk/admin revocation ("log out all sessions for user X") — would need indexing revoked tokens
  by username, not just `jti`; not asked for, adds a query pattern nothing here needs yet.
- Cleanup/pruning job for expired rows in `revoked_tokens` — the table only grows on explicit
  logout (not automatically per-login), so volume stays low for a demo; `expires_at` is stored
  specifically so a pruning query could be added later without a schema change, but no job is
  built now.
