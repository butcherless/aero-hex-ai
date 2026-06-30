# Authentication — JWT with Tapir + ZIO

Analysis of the authentication layer to implement on top of the existing hexagonal architecture.
Covers library selection, Tapir security patterns, new file layout, and open decisions.

---

## Recommendation

**`jwt-scala` (`jwt-circe` module) + `jbcrypt` (Java)**

No other combination matches on all criteria (Scala 3, Circe-native, active maintenance,
idiomatic ZIO wrapping, broad community adoption).

---

## JWT library comparison

| Library | Scala 3 | Circe native | Maintenance | Community | ZIO fit |
|---|---|---|---|---|---|
| **jwt-scala / jwt-circe** | ✓ | ✓ native | Active (jwt-scala org) | Highest | `ZIO.attempt` wrap |
| tsec | Partial | Via cats | Stalled (last release 2021) | Declining | cats-effect only |
| nimbus-jose-jwt | ✓ (Java) | Manual | Active (Java) | Java-centric | `ZIO.attempt` wrap |
| zio-jwt | — | — | Does not exist as standalone | — | — |

`jwt-scala` wins. The `jwt-circe` module speaks Circe natively — no adapters, no conversion
layer. Circe is already a direct dependency of this project. The library exposes pure functions
(`JwtCirce.encode`, `JwtCirce.decode`) with no side effects, making ZIO wrapping trivial.

Current stable: **10.0.1** (`io.github.jwt-scala`, Scala 3).

---

## Password hashing

**`org.mindrot:jbcrypt` 0.4** — the reference BCrypt implementation (Java).
Wrapping two calls in `ZIO.attempt` is sufficient; no Scala wrapper adds value.

```scala
import org.mindrot.jbcrypt.BCrypt

def hash(plain: String): Task[String] =
  ZIO.attempt(BCrypt.hashpw(plain, BCrypt.gensalt(12)))

def verify(plain: String, hash: String): Task[Boolean] =
  ZIO.attempt(BCrypt.checkpw(plain, hash))
```

---

## Key API patterns

### JWT generation and validation

```scala
import pdi.jwt.{JwtCirce, JwtClaim, JwtAlgorithm}
import io.circe.syntax.*
import java.time.Clock

given Clock = Clock.systemUTC

// Generate
val claim = JwtClaim()
  .about(userId)     // sub
  .issuedNow         // iat
  .expiresIn(3600)   // exp (+1 hour)
JwtCirce.encode(claim + payload.asJson.noSpaces, secretKey, JwtAlgorithm.HS256)

// Validate — exp/nbf checked automatically; returns scala.util.Try[JwtClaim]
JwtCirce.decode(token, secretKey, Seq(JwtAlgorithm.HS256))
```

ZIO wrappers (live in `JwtService`):

```scala
def generate(payload: JwtPayload): Task[String] =
  ZIO.attempt(JwtCirce.encode(...))

def validate(token: String): IO[AuthError, JwtPayload] =
  ZIO.fromTry(JwtCirce.decode(token, secretKey, Seq(JwtAlgorithm.HS256)))
    .mapError(_ => AuthError.InvalidToken)
    .flatMap(claim =>
      ZIO.fromEither(claim.content.as[JwtPayload])
        .mapError(_ => AuthError.MalformedClaims)
    )
```

### Tapir security pattern

Tapir's two-phase security API maps directly to this use case.

```scala
// 1 — shared secured base: defined once, reused by all protected endpoints
val securedEndpoint =
  endpoint
    .securityIn(auth.bearer[String]())   // reads Authorization: Bearer <token>
    .errorOut(/* unified error output */)
    .zServerSecurityLogic[Any] { token =>
      jwtService.validate(token).mapError(ErrorMapper.toHttpError)
    }

// 2 — protected endpoint extends the base; receives resolved AuthenticatedUser
val getMe =
  securedEndpoint.get
    .in("api" / "v1" / "auth" / "me")
    .out(jsonBody[ProfileDto])
    .zServerLogic[Any] { authenticatedUser => _ =>
      profileService.get(authenticatedUser).mapError(ErrorMapper.toHttpError)
    }
```

The login endpoint is **public** (no `securityIn`):

```scala
val login: PublicEndpoint[LoginRequest, (StatusCode, HttpErrorResponse), TokenResponse, Any] =
  endpoint.post
    .in("api" / "v1" / "auth" / "login")
    .in(jsonBody[LoginRequest])
    .out(jsonBody[TokenResponse])
    .errorOut(/* 401 Unauthorized + 400 Bad Request */)
```

---

## New files — hexagonal layout

```
domain/
  model/
    AuthenticatedUser.scala      ← value object: userId, roles
  error/
    DomainError.scala            ← add: InvalidCredentials, InvalidToken
  port/in/
    AuthUseCase.scala            ← login(username, password): IO[DomainError, JwtToken]
  port/out/
    UserRepository.scala         ← findByUsername(username): IO[DomainError, Option[User]]
    TokenService.scala           ← generate(payload): Task[String]
                                    validate(token): IO[DomainError, JwtPayload]

application/
  service/
    AuthService.scala            ← implements AuthUseCase; calls UserRepository + TokenService

infrastructure/
  persistence-postgres/
    DoobieUserRepository.scala   ← Doobie impl of UserRepository (users table)
  auth/                          ← new package (or thin module)
    JwtService.scala             ← implements TokenService; wraps jwt-circe
    JwtConfig.scala              ← secretKey, algorithm, ttlSeconds read from env

adapter-http/
  dto/
    AuthDto.scala                ← LoginRequest, TokenResponse, ProfileDto
  endpoint/
    AuthEndpoints.scala          ← login val + securedEndpoint base
    AuthRoutes.scala             ← wires AuthUseCase; adds serverEndpoints to the list
```

---

## Database migration

A new **V6** Flyway migration is needed:

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(60)  NOT NULL,         -- BCrypt output is always 60 chars
    roles         TEXT[]       NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

---

## `build.sbt` additions

```scala
// Versions.scala
val jwtScala = "10.0.1"
val jbcrypt  = "0.4"

// auth infrastructure module (or persistence-postgres if kept together)
libraryDependencies ++= Seq(
  "io.github.jwt-scala" %% "jwt-circe" % Versions.jwtScala,
  "org.mindrot"          % "jbcrypt"   % Versions.jbcrypt
)
```

`jwt-circe` transitively pulls in `jwt-core` and Circe — no duplication since Circe is
already a direct dependency.

---

## Open decisions

| Topic | Decision needed |
|---|---|
| Secret key storage | Env var / secrets manager only — never hardcoded. `JwtConfig` read at boot, wired via `ZLayer`. |
| Algorithm | HS256 for single-service (shared secret). Switch to RS256 if multiple services verify tokens independently. |
| Token TTL | Suggest 1 hour (access token). Refresh token strategy is out of scope for first iteration. |
| Roles in claims | Embed roles in the JWT payload to avoid a DB lookup on every authenticated request. |
| `InvalidCredentials` wording | Must NOT reveal whether username or password was wrong — prevents user enumeration attacks. |
| Token refresh | Out of scope for this analysis. Requires a `refresh_token` column in `users` and a separate endpoint. |
| Test strategy | `JwtService` is pure enough to test with `ZIO.attempt` and fixed clock injection. `AuthService` tests mock `UserRepository` and `TokenService` ports. |
