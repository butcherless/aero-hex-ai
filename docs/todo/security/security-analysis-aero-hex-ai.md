# Security Analysis for aero-hex-ai — Authentication, Authorization, and Fine-Grained ACL

> **Project:** aero-hex-ai
> **Stack:** Scala 3 · ZIO 2 · zio-http · Hexagonal Architecture
> **Scope:** Recommended libraries for authentication and authorization in the
> ZIO ecosystem, and where fine-grained ACL belongs in a hexagonal layered
> architecture.
> **Status:** Abstract analysis, superseded for implementation purposes by
> `docs/todo/auth-jwt.md`'s `## Roadmap` (the living step-by-step tracker) and
> `plans/security/*.md` (per-step design docs) — kept here as historical rationale;
> each step's plan doc says exactly which parts of this analysis it adapted.

---

## 1. Problem Breakdown

Security in this context splits into three distinct concerns, each with a
different natural home in the architecture:

| Concern | Question it answers | Needs the resource loaded? |
|---|---|---|
| **Authentication** | Who are you? | No |
| **Coarse-grained authorization** | Do you generally hold role/permission X? | No |
| **Fine-grained ACL** | Can you act on *this specific* resource instance? | **Yes** |

---

## 2. Authentication — Options

### Option A — Manual JWT with `HandlerAspect` (official zio-http pattern)

This is the pattern shown directly in the official ZIO HTTP documentation
and example repository (`zio/zio-http`): a `HandlerAspect` intercepts the
incoming request, decodes the JWT using `jwt-scala`, and passes the
authenticated subject into the handler's context.

```scala
//> using dep "dev.zio::zio-http:3.4.0"
//> using dep "com.github.jwt-scala::jwt-core:10.0.4"

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

val bearerAuthWithContext: HandlerAspect[Any, String] =
  HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
    request.header(Header.Authorization) match
      case Some(Header.Authorization.Bearer(token)) =>
        ZIO.fromTry(Jwt.decode(token.value.asString, SECRET_KEY, Seq(JwtAlgorithm.HS512)))
          .orElseFail(Response.badRequest("Invalid or expired token!"))
          .flatMap(claim => ZIO.fromOption(claim.subject).orElseFail(Response.badRequest("Missing subject claim!")))
          .map(u => (request, u))
      case _ =>
        ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))))
  })
```

**Pros:** no extra "security framework" dependency, full control, lightweight.
**Cons:** you implement claim validation, expiration, remote JWKS, etc.
yourself.

### Option B — `zio-jwt-validator` — for validating tokens from an external IdP

```scala
libraryDependencies += "io.github.janlisse" %% "zio-jwt-validator" % "0.1.0"
```

A ZIO-based library for validating JWT tokens, including fetching keys from
JWKS over HTTPS and validating claims such as audience and issuer. Built on
`zio`, `zio-json`, `zio-http`, and `jwt-scala`.

```scala
val program = for
  _ <- JwtValidator.validate(jwtToken)
  _ <- ZIO.logInfo("Successfully validated token.")
yield ()
```

**Maturity note:** version `0.1.0`, small and recent project. Currently only
RSA-signed token validation is supported (works well with providers like
Auth0); elliptic curve signature support is planned.

### Option C — `zio-http-pac4j` — full OAuth2/OIDC/SAML integration

```scala
libraryDependencies ++= Seq(
  "me.seroperson" %% "zio-http-pac4j" % "0.1.1",
  "org.pac4j"      % "pac4j-http"     % "6.2.1"
)
```

A zio-http wrapper for pac4j, a mature Java security framework that allows
easy implementation of authorization and authentication mechanisms
(Google, GitHub, OIDC, SAML, simple login+password forms).

```scala
Method.GET / Root -> Handler.fromFunctionZIO { (req: Request) =>
  for
    profile  <- ZIO.service[UserProfile]
    response <- ZIO.succeed(Response.html(/* ... */))
  yield response
} @@ Pac4jMiddleware.securityFilter(authorizers = List("IsFullyAuthenticatedAuthorizer"))
```

**Pros:** pac4j itself is very mature (also used via `http4s-pac4j` and
`play-pac4j`) — years of robustness in OAuth2/OIDC/SAML inherited for free.
**Cons:** the ZIO wrapper is recent (v0.1.1); some "javish" pac4j APIs leak
through.

### Comparison

| Option | Core maturity | ZIO wrapper maturity | Best fit |
|---|---|---|---|
| Manual JWT + `HandlerAspect` | High (`jwt-scala` is mature) | Official zio-http pattern | Full control, self-issued JWTs |
| `zio-jwt-validator` | Medium | Low (0.1.0, niche) | Validating JWTs from an external IdP (Auth0/Keycloak) without managing JWKS yourself |
| `zio-http-pac4j` | High (pac4j) | Low (0.1.1, very recent) | Full OAuth2/OIDC/SAML, social login, multi-provider |

**Recommendation for aero-hex-ai:** since the system likely issues its own
JWTs (no dependency on an external IdP like Auth0), **Option A (manual JWT +
`HandlerAspect`)** aligns best — it's the same port/adapter style already in
use, with no heavy security framework dependency.

---

## 3. Coarse-Grained Authorization — `zio-http-authorization`

```scala
libraryDependencies += "me.mbauer83" %% "zio-http-authorization" % "x.x.x" // check latest version
```

A library for basic effectful role-based and permission-based access
control in ZIO-http applications. Key concepts:

- **Role** — a tag associated with users in a many-to-many fashion (e.g.
  `SUPER`, `ADMIN`, `USER`)
- **Permission** — a tag representing an action a user may perform on a
  `Resource`, also many-to-many but tupled with a descriptive
  `ResourceSelector`
- **AuthorizationPolicy** — encapsulates authorization logic for a specific
  `User`/`Resource` type pair; defines an `authorize` method returning a ZIO
  effect that either fails with `UserNotAuthorizedForResourceException` or
  succeeds with the (filtered) resource

This `ResourceSelector`-tupled-with-`Permission` concept is precisely the
bridge toward fine-grained ACL, covered next.

---

## 4. Fine-Grained ACL — Where It Belongs

This is the architecturally interesting part. Fine-grained ACL ("can this
user edit *this specific* `Route`, not just Routes in general?") is not a
single-layer concern — it splits across two levels.

### Level 1 — Structural authorization (do you hold the general role/permission?) → **HTTP Adapter**

```scala
// adapter-http — middleware/aspect, before reaching the use case
val routes = Method.PUT / "routes" / string("routeId") ->
  handler @@ RequirePermission("route:update")  // structural check, resource not yet loaded
```

This layer answers: *"does this user, in general, have permission to update
routes?"* — it does not need to load the resource yet.

### Level 2 — Fine-grained ACL on the specific resource → **Application layer**, never in `domain`

The key insight: **fine-grained ACL needs the resource loaded to be
evaluated** (e.g. "is this user the owner of this specific `Route`?", "does
this `Airline` belong to the user's organization?"). That requires a query —
so it cannot live in `domain/` (which must stay pure), and it cannot be
fully resolved in the HTTP adapter either (which shouldn't know business
rules or access repositories directly).

```scala
// application/service/UpdateRouteService.scala
final class UpdateRouteService(
    routeRepo: RouteRepository,
    aclPolicy: AuthorizationPolicy[User, Route]   // fine-grained ACL, injected as a port
):
  def update(user: User, routeId: RouteId, changes: RouteUpdate): IO[AppError, Route] =
    for
      route   <- routeRepo.findById(routeId).someOrFail(AppError.NotFound)
      _       <- aclPolicy.authorize(route, user)     // ACL evaluated HERE, resource already loaded
                   .mapError(_ => AppError.Forbidden)
      updated <- routeRepo.save(route.applyChanges(changes))
    yield updated
```

`zio-http-authorization` itself models this exactly this way — the
`authorize` method takes the already-loaded `Resource` and the `User`, and
returns a ZIO effect. This confirms the community-standard approach: ACL
evaluated in the application layer, with the resource already resolved.

### Level 3 — Pure ownership/business invariants → **Domain layer** (if no I/O is required)

If the ACL check can be expressed without any further lookup (e.g. "a user
can only edit routes belonging to their own airline, and both pieces of
data are already in memory"), the comparison itself can be a pure function
in `domain/service/`:

```scala
// domain/service/RouteOwnership.scala — pure, no I/O
object RouteOwnership:
  def canEdit(user: User, route: Route): Boolean =
    user.organizationId == route.ownerOrganizationId
```

But the **orchestration** (loading the resource, invoking the check,
deciding to fail with 403) still lives in `application/`.

---

## 5. Summary — Layers and Responsibilities

| Control type | Lives in | Needs resource loaded | Library |
|---|---|---|---|
| Authentication (who are you?) | `adapter-http` (middleware/`HandlerAspect`) | No | Manual JWT / `zio-jwt-validator` / `zio-http-pac4j` |
| Coarse-grained authorization (general role/permission) | `adapter-http` (middleware, before use case) | No | `zio-http-authorization` |
| **Fine-grained ACL** (can I act on *this* resource?) | **`application/service`** (orchestrates: load resource → invoke policy → fail or continue) | **Yes** | `zio-http-authorization` (`AuthorizationPolicy`) injected as a service dependency |
| Pure "ownership" invariant (no extra I/O) | `domain/service` (pure function, given resource and user already in memory) | Already in memory | No library — custom code |

**Guiding principle:** fine-grained ACL cannot live solely in the HTTP
adapter, because at that point the resource is not yet loaded (only the
`routeId` from the path is available) — checking there would require a
duplicate query that the use case is going to perform anyway. The correct
pattern is: **general role/permission → HTTP; resource-specific ACL →
application, immediately after loading the resource and before
mutating/returning it.**

---

## 6. Reference Links

- ZIO HTTP official authentication example: https://zio.dev/zio-http/examples/authentication
- `HandlerAspect` reference: https://zio.dev/zio-http/reference/aop/handler_aspect
- `zio-http-pac4j`: https://github.com/seroperson/zio-http-pac4j
- `zio-jwt-validator`: https://github.com/janlisse/zio-jwt-validator
- `zio-http-authorization`: https://github.com/mbauer83/zio-http-authorization
- ZIO HTTP Basic Authentication guide: https://ziohttp.com/guides/basic-authentication/
