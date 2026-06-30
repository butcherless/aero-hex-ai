# Authentication — JWT with Tapir + ZIO

> **Status:** Analysis — not yet implemented.
> Covers library selection, architectural fit, API design, implementation patterns, and open decisions.

---

## 1. Technology selection

### 1.1 JWT

**Selected: `jwt-scala` / `jwt-circe` module — version 10.0.1**
(`io.github.jwt-scala`, Scala 3, actively maintained by the jwt-scala organisation)

| Library | Scala 3 | Circe integration | Maintenance | Notes |
|---|---|---|---|---|
| **jwt-scala / jwt-circe** | ✓ | Native | Active | Best fit — see rationale below |
| tsec | Partial | Via cats | Stalled (2021) | cats-effect only; incompatible with ZIO |
| nimbus-jose-jwt | ✓ (Java) | Manual | Active | Java-centric; no Scala idioms |

`jwt-circe` is a first-class Circe extension of `jwt-scala`, not an adapter. Since Circe is
already a direct project dependency, there is zero integration overhead. The library exposes
pure functions (`JwtCirce.encode`, `JwtCirce.decode`) with no side effects; each call is
wrapped in `ZIO.attempt` at the infrastructure boundary.

### 1.2 Password hashing

**Selected: `org.mindrot:jbcrypt` 0.4** — the reference BCrypt implementation (Java).

BCrypt is the standard choice for password hashing: adaptive cost factor, built-in salt,
fixed 60-character output. No Scala wrapper is needed; `ZIO.attempt` covers the Java call.

### 1.3 Build dependencies

```scala
// Versions.scala
val jwtScala = "10.0.1"
val jbcrypt = "0.4"

// infrastructure/auth (new module or added to persistence-postgres)
libraryDependencies ++= Seq(
  "io.github.jwt-scala" %% "jwt-circe" % Versions.jwtScala,
  "org.mindrot" % "jbcrypt" % Versions.jbcrypt
)
```

---

## 2. Architecture

The authentication feature follows the same hexagonal conventions as the existing Country
slice: the domain defines the contract (ports), the application layer orchestrates, and
infrastructure adapters implement.

### 2.1 File layout

```
domain/
  model/
    AuthenticatedUser.scala      ← value object carried through secured requests (userId, roles)
  error/
    DomainError.scala            ← add: InvalidCredentials, InvalidToken
  port/in/
    AuthUseCase.scala            ← driving port: login(username, password): IO[DomainError, JwtToken]
  port/out/
    UserRepository.scala         ← driven port: findByUsername(u): IO[DomainError, Option[User]]
    TokenService.scala           ← driven port: generate / validate — abstracts JWT technology

application/
  service/
    AuthService.scala            ← implements AuthUseCase; orchestrates UserRepository + TokenService

infrastructure/
  persistence-postgres/
    DoobieUserRepository.scala   ← Doobie implementation of UserRepository
  auth/
    JwtService.scala             ← implements TokenService; wraps jwt-circe in ZIO effects
    JwtConfig.scala              ← secretKey, algorithm, ttlSeconds — read from environment

adapter-http/
  dto/
    AuthDto.scala                ← LoginRequest, TokenResponse, ProfileDto
  endpoint/
    AuthEndpoints.scala          ← Tapir endpoint definitions + reusable securedEndpoint base
    AuthRoutes.scala             ← wires AuthUseCase into server logic
```

### 2.2 Key design points

- **`TokenService` is a domain port** (`port/out`), not a domain concept. The domain knows
  what a token is (an opaque `JwtToken` value type) but not how it is generated. This keeps
  `jwt-circe` confined to the infrastructure layer.
- **`AuthenticatedUser`** is the principal resolved by the security logic and threaded into
  protected use cases as a context parameter — equivalent to a security context in other
  frameworks.
- **`JwtConfig`** is read at bootstrap from environment variables, never hardcoded, and
  provided to `JwtService` via `ZLayer`.

---

## 3. REST API

### 3.1 Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | Validate credentials; return access token |
| `GET` | `/api/v1/auth/me` | Bearer | Return the authenticated user's profile |

### 3.2 Schemas

**`POST /api/v1/auth/login`**

```
Request  { username: String, password: String }
Response { token: String, tokenType: "Bearer", expiresIn: Int }
Errors   400 Bad Request (missing fields) | 401 Unauthorized (invalid credentials)
```

**`GET /api/v1/auth/me`**

```
Header   Authorization: Bearer <token>
Response { username: String, roles: List[String] }
Errors   401 Unauthorized (missing/invalid/expired token)
```

---

## 4. Implementation patterns

### 4.1 JWT operations (`JwtService`)

```scala
import pdi.jwt.{JwtCirce, JwtClaim, JwtAlgorithm}
import io.circe.syntax.*
import java.time.Clock

given Clock = Clock.systemUTC

// Generate — returns a signed, time-bound token
def generate(payload: JwtPayload): Task[String] =
  ZIO.attempt {
    val claim = JwtClaim()
      .about(payload.userId) // sub
      .issuedNow // iat
      .expiresIn(config.ttl) // exp
    JwtCirce.encode(claim + payload.asJson.noSpaces, config.secretKey, JwtAlgorithm.HS256)
  }

// Validate — exp/nbf are checked automatically by jwt-scala
def validate(token: String): IO[DomainError, JwtPayload] =
  ZIO.fromTry(JwtCirce.decode(token, config.secretKey, Seq(JwtAlgorithm.HS256)))
    .mapError(_ => DomainError.InvalidToken)
    .flatMap(claim =>
      ZIO.fromEither(claim.content.as[JwtPayload])
        .mapError(_ => DomainError.InvalidToken)
    )
```

### 4.2 Password verification (`AuthService`)

```scala
import org.mindrot.jbcrypt.BCrypt

// During registration (user creation)
def hashPassword(plain: String): Task[String] =
  ZIO.attempt(BCrypt.hashpw(plain, BCrypt.gensalt(12)))

// During login
def verifyPassword(plain: String, hash: String): Task[Boolean] =
  ZIO.attempt(BCrypt.checkpw(plain, hash))
```

### 4.3 Tapir two-phase security

Tapir models authentication as two separate phases: security logic (phase 1) runs before
business logic (phase 2) and resolves the caller's identity. A `securedEndpoint` base is
defined once and composed into every protected endpoint.

```scala
// Phase 1 — shared base: validates the bearer token, resolves AuthenticatedUser
val securedEndpoint =
  endpoint
    .securityIn(auth.bearer[String]()) // reads Authorization: Bearer <token>
    .errorOut(/* 401 error output */)
    .zServerSecurityLogic[Any] { token =>
      jwtService.validate(token).mapError(ErrorMapper.toHttpError)
    }

// Phase 2 — protected endpoint: receives the resolved principal from phase 1
val getMe =
  securedEndpoint.get
    .in("api" / "v1" / "auth" / "me")
    .out(jsonBody[ProfileDto])
    .zServerLogic[Any] { authenticatedUser =>
      _ =>
        ZIO.succeed(ProfileDto.fromPrincipal(authenticatedUser))
    }

// Login is public — no securityIn, standalone PublicEndpoint
val login: PublicEndpoint[LoginRequest, (StatusCode, HttpErrorResponse), TokenResponse, Any] =
  endpoint.post
    .in("api" / "v1" / "auth" / "login")
    .in(jsonBody[LoginRequest])
    .out(jsonBody[TokenResponse])
    .errorOut(/* 400 + 401 */)
```

---

## 5. Database migration (V6)

```sql
CREATE TABLE users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash CHAR(60)     NOT NULL, -- BCrypt always produces 60 characters
    roles         TEXT[]       NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

---

## 6. Open decisions

| Topic | Recommendation | Notes |
|---|---|---|
| Secret key storage | Environment variable only; `JwtConfig` via `ZLayer` | Never hardcode; rotate without redeploy |
| Signing algorithm | HS256 (symmetric shared secret) | Switch to RS256 if multiple independent services need to verify tokens |
| Access token TTL | 1 hour | Short-lived tokens reduce the exposure window on compromise |
| Token refresh | Out of scope — first iteration only | Requires a `refresh_token` column in `users` and a dedicated endpoint |
| Roles in token | Embed in JWT payload | Avoids a DB round-trip on every authenticated request |
| Error messages | Generic "invalid credentials" for both wrong username and wrong password | Prevents user enumeration attacks |
| Test strategy | Inject a fixed `Clock` into `JwtService` for deterministic expiry tests; mock `UserRepository` and `TokenService` ports in `AuthService` unit tests | |
