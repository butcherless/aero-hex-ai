# Validation in Hexagonal Architecture — Analysis

> **Project:** aero-hex-ai
> **Example used:** `Country(code, name)` — a 2-letter code that must be both
> syntactically valid (2 letters) and referentially valid (must exist in the
> `countries` table)
> **Decision:** split validation across `domain/model` (syntax) and
> `domain/service` + `port.out` (existence). See §2.

---

## 1. The core insight — validation is not one concern

For a field as simple as `Country.code`, there are actually **three distinct
validations**, not one. Conflating them is what makes this decision feel hard.

| Validation | Nature | Example | Needs I/O? | Changes how often? |
|---|---|---|---|---|
| **Syntactic** | Format/shape | 2 letters, uppercase, non-empty | No | Almost never |
| **Semantic/domain invariant** | Business-meaningful shape | "ES" is a plausible code | No | Rarely |
| **Referential/existence** | Lookup against known data | "ES" exists in `countries`; "XX" doesn't | **Yes** | Can change (new/deprecated codes) |

The referential check is the crux of the problem: **"is this a real country"
is not a pure function of the string** — it's a lookup against a reference
dataset (the `countries` table). That fact drives where it can live: a pure
domain smart constructor cannot query a database, so existence checks must
sit behind a `port.out`, not inside `CountryCode.apply`.

---

## 2. The approach — Value Object (pure) + Domain Service backed by a Port

### 2.1 Current state in aero-hex-ai (starting point)

Neither half of this split is actually wired up today:

- **Syntax**: `domain/model/Country.scala` already has
  `CountryCode.from(value: String): Option[CountryCode]`, which implements
  exactly the 2-letter/alphabetic check — but it's dead code, never called.
  Every call site uses `CountryCode.apply`, which performs no validation at
  all. The only place the 2-letter/alpha shape is actually enforced today is
  the HTTP boundary (`Validator.pattern(CodePatterns.alpha2)` in
  `adapter-http/.../endpoint/CountryEndpoints.scala`, `AirportEndpoints.scala`
  for `countryCodeParam`) — this is BR-01 in
  `docs/analysis/01-domain-model.md`.
- **Existence**: for Airport/Airline/Aircraft (whose parent — Country or
  Airline — must already exist, BR-04), the check is not a dedicated
  `domain/service` at all. It's embedded directly inside the **persistence
  adapter**: `QuillCountryIdResolver.resolveCountryId`
  (`infrastructure/persistence-quill/.../QuillCountryIdResolver.scala`) and
  `DoobieIdResolver.resolveId`
  (`infrastructure/persistence-postgres/.../DoobieIdResolver.scala`) run the
  lookup query themselves and fail with `DomainError.CountryNotFound`/
  `AirlineNotFound` if nothing matches — invoked only when `save`/`update`
  actually runs, from inside `QuillAirportRepository`/`QuillAirlineRepository`/
  `QuillAircraftRepository` (same for the Doobie adapters). Application
  services (e.g. `CreateAirportService`) never call an explicit
  existence-check step themselves; they only pre-check the entity's *own*
  uniqueness (`repo.findByIata` before `save`) and delegate everything about
  the parent to the repository.

Adopting the split below means: activating `CountryCode.from` as the real
constructor, and pulling the existence check out of the persistence adapter
into an explicit `domain/service` step that application services call
**before** `save`/`update` — so a missing parent fails fast, before ever
reaching the database.

### 2.2 B1 — `CountryCode`: Value Object, syntactic only, pure smart constructor

```scala
// domain/src/main/scala/dev/cmartin/aerohex/domain/model/Country.scala
package dev.cmartin.aerohex.domain.model

opaque type CountryCode = String

object CountryCode:
  def from(value: String): Option[CountryCode] =
    Option.when(value.length == 2 && value.forall(_.isLetter))(value)
  extension (c: CountryCode) def value: String = c
```

This already exists — `from` just needs to become the constructor every call
site uses, replacing the current no-op `apply`. Pure, total, zero I/O,
trivially unit-testable. Answers only: *"is this string shaped like a
country code?"* Application/HTTP call sites become:

```scala
ZIO.fromOption(CountryCode.from(rawCode))
  .orElseFail(DomainError.InvalidCountryCode(rawCode))
```

`InvalidCountryCode` doesn't exist in `domain/error/DomainError.scala` yet —
it would need to be added, following the one existing precedent for a
validation-shaped error, `InvalidRoute(reason: String)`.

### 2.3 B2 — Existence check: domain service backed by the existing `port.out`

No new port method is needed — `CountryRepository.findByCode` already
returns `IO[DomainError, Option[Country]]`
(`domain/src/main/scala/dev/cmartin/aerohex/domain/port/out/CountryRepository.scala`),
which is exactly what an existence check needs:

```scala
// domain/src/main/scala/dev/cmartin/aerohex/domain/service/CountryValidationService.scala
package dev.cmartin.aerohex.domain.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.CountryCode
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, ZIO}

object CountryValidationService:
  def validateExists(code: CountryCode): ZIO[CountryRepository, DomainError, CountryCode] =
    ZIO.serviceWithZIO[CountryRepository](_.findByCode(code)).flatMap {
      case Some(_) => ZIO.succeed(code)
      case None    => ZIO.fail(DomainError.CountryNotFound(code.value))
    }
```

`CountryNotFound` already exists in `DomainError` — no new case needed here,
unlike B1's format error.

### 2.4a `RESOLVED` — Country's own code now has a referential check, but not via B2/B3 as written

`BR-16` (`docs/analysis/01-domain-model.md`) implements a referential check
for `Country.code` itself — but it isn't the check §2.3's example describes,
and it's worth being precise about why. §2.3 frames the existence check as
"does this code exist in `countries`" — that framing only makes sense for a
code *referenced by* another entity (an Airport's `countryCode`, checked
against `countries` by `QuillCountryIdResolver`, already live — see BR-04).
It cannot be the check for a **new** `Country` being created: `countries` is
exactly the table `CreateCountryService.create`'s existing `AlreadyExists`
check already queries, and checking a new row's code against the row you're
about to insert is circular.

What a new `Country`'s code actually needs is a check against an independent
reference dataset — "is this a real ISO 3166-1 alpha-2 code at all,"
regardless of whether this app has ever created a row for it. That's
`V12__create_country_codes.sql`: a standalone 249-row table, deliberately
**not** FK'd to `countries`, existing purely for this lookup. The
implementation is simpler than B2/B3's blueprint — one new method directly
on `CountryRepository` (`isValidCode`), called inline from
`CreateCountryService.create`, no separate `domain/service` class:

```scala
// domain/src/main/scala/dev/cmartin/aerohex/domain/port/out/CountryRepository.scala
trait CountryRepository {
  def isValidCode(code: CountryCode): IO[DomainError, Boolean]
  // ...
}
```

```scala
// application/src/main/scala/dev/cmartin/aerohex/application/service/CreateCountryService.scala
override def create(command: CreateCountryCommand): IO[DomainError, Country] =
  val effect =
    repo.isValidCode(command.code).flatMap:
      case false => ZIO.fail(DomainError.InvalidCountryCode(command.code.value))
      case true  =>
        repo.findByCode(command.code).flatMap:
          case Some(_) => ZIO.fail(DomainError.CountryAlreadyExists(command.code.value))
          case None    => repo.save(Country(command.code, command.name))
  effect @@ ServiceAspect.logged(s"CreateCountryService.create(${command.code.value})")
```

§2.2's `CountryCode.from` is still dead code — this shipped instead of it,
not on top of it. The two aren't redundant: `from`'s 2-letter/alphabetic
check would still reject `"123"` or `"E"` before ever reaching the database;
`isValidCode` only rejects *real-shaped* codes that aren't *real* codes
(`"ZZ"`, `"XX"`). Today only the latter is enforced (via HTTP `Validator`s
for shape, `isValidCode` for membership) — `from` activating BR-01 at the
domain layer remains exactly the open item §2.2 describes.

§2.2–2.4's original subject — an Airport/Airline/Aircraft validating that
its *referenced* parent exists — is still unimplemented as a `domain/service`.
That check lives where it always has: reactively, inside the persistence
adapters (`QuillCountryIdResolver.resolveCountryId`,
`QuillAirlineIdResolver.resolveAirlineId`), not proactively in `application/`.

### 2.4 B3 — Use case orchestrates both

Today, `CreateAirportService.create`
(`application/src/main/scala/dev/cmartin/aerohex/application/service/CreateAirportService.scala`)
only pre-checks the Airport's own uniqueness before delegating to
`AirportRepository.save`, which resolves `countryCode` internally:

```scala
final class CreateAirportService(repo: AirportRepository) extends CreateAirportUseCase:
  override def create(command: CreateAirportCommand): IO[DomainError, Airport] =
    val effect = repo.findByIata(command.iataCode).flatMap:
      case Some(_) => ZIO.fail(DomainError.AirportAlreadyExists(command.iataCode.value))
      case None    =>
        repo.save(Airport(command.iataCode, command.icaoCode, command.name, command.city), command.countryCode)
    effect @@ ServiceAspect.logged(s"CreateAirportService.create(${command.iataCode.value})")
```

Under this split, the country-existence check moves up into the service,
failing fast before any write is attempted:

```scala
final class CreateAirportService(repo: AirportRepository, countryRepo: CountryRepository) extends CreateAirportUseCase:
  override def create(command: CreateAirportCommand): IO[DomainError, Airport] =
    val effect =
      for
        _        <- CountryValidationService.validateExists(command.countryCode).provideService(countryRepo)
        existing <- repo.findByIata(command.iataCode)
        airport  <- existing match
                      case Some(_) => ZIO.fail(DomainError.AirportAlreadyExists(command.iataCode.value))
                      case None    =>
                        repo.save(Airport(command.iataCode, command.icaoCode, command.name, command.city), command.countryCode)
      yield airport
    effect @@ ServiceAspect.logged(s"CreateAirportService.create(${command.iataCode.value})")
```

**Why this split is architecturally correct:** `CountryCode` stays in
`domain/model/`, pure, zero dependencies, trivially testable in isolation.
The *existence* check becomes a `domain/service` call against the existing
`port.out.CountryRepository` — "does this country exist" is fundamentally a
query against a driven adapter (Postgres via Quill), not a pure function, so
it belongs behind a port rather than inside a constructor. The Postgres FK
(`airports.country_id → countries.id`, `V7__add_surrogate_keys.sql`) still
stays in place underneath as a last-resort safety net — belt-and-braces, not
a competing mechanism.

---

## 3. Recommendation for aero-hex-ai

Place each validation concern as follows:

```
adapter-http/     → HTTP-shape validation only: JSON has a "code" field of
                     type string, non-null, non-empty (schema-level check,
                     nothing business-specific). Currently this layer does
                     MORE than that (the full BR-01 pattern check lives
                     here today, via CountryEndpoints.scala's codeParam) —
                     that check moves to domain/model under this plan.

domain/model/      → CountryCode.from: 2-letter format, uppercase
                     normalization (pure, total, no I/O)
                     — this is BR-01: "country code format"

domain/port/out/   → CountryRepository.findByCode — already exists,
                     unchanged signature, reused for existence checks

domain/service/    → CountryValidationService.validateExists(code) — new,
                     calls CountryRepository.findByCode
                     — this is BR-04: "an Airport's/Airline's Country must
                     already exist"

infrastructure/    → QuillCountryRepository / DoobieCountryRepository —
                     the existing Postgres/Quill query implementing the port
                     (unchanged)

application/       → orchestrates: parse (domain/model) → check existence
                     (via domain/service) → proceed with the use case
                     (CreateAirportService, CreateAirlineService,
                     CreateAircraftService)
```

This preserves `domain`'s core promise — **zero infrastructure
dependencies** — while keeping a single, non-duplicated source of truth for
"which countries exist" (the `countries` table), instead of hardcoding a
second, driftable copy of ISO 3166 inside the domain module.

---

## 4. Reusable pattern for other value objects

The same B-pattern applies to any value object in the domain that has both
a syntactic shape and a referential existence check:

| Value Object | File | Syntactic check (domain/model) | Existence check (domain/service + port.out) |
|---|---|---|---|
| `CountryCode` | `domain/model/Country.scala` | 2 letters, alphabetic (BR-01) | exists in `countries` (BR-04, via `CountryRepository.findByCode`) |
| `IataCode` | `domain/model/Airport.scala` | 3 letters, alphabetic (BR-02) | exists in `airports` (via `AirportRepository.findByIata`) |
| `IcaoCode` | `domain/model/Airline.scala` (shared with `Airport`/`Route`/`Flight`/`Aircraft`) | 4 letters for Airport, 3 letters for Airline (BR-03) | exists in `airlines` (BR-04, via `AirlineRepository.findByIcao`) |
| `Registration` | `domain/model/Aircraft.scala` | non-blank, ≤ 10 chars, deliberately no shape pattern (BR-15 — real-world registrations vary by country) | exists in `aircraft` (via `AircraftRepository.findByRegistration`) |

Cross-entity invariants that are **not** simple existence checks — e.g.
BR-07 "a route's origin airport must differ from its destination airport" —
are pure and can live entirely in a `domain/service` **without** a port,
since both `IataCode` values are already in hand and no I/O is needed. This
one is already implemented exactly this way:

```scala
// domain/src/main/scala/dev/cmartin/aerohex/domain/service/RouteValidator.scala
object RouteValidator {
  def validate(origin: IataCode, destination: IataCode, distanceKm: Int): IO[DomainError, Unit] =
    for {
      _ <- ZIO.fail(InvalidRoute("Origin and destination cannot be the same airport"))
             .when(origin == destination)
      _ <- ZIO.fail(InvalidRoute(s"Distance must be positive, got $distanceKm"))
             .when(distanceKm <= 0)
    } yield ()
}
```

(BR-08 — distance must be positive — is enforced in the same pass.)

---

## 5. Summary

| Validation kind | Belongs in | Requires I/O | Example |
|---|---|---|---|
| Format/shape | `domain/model` (smart constructor) | No | `CountryCode.from` must be 2 letters (BR-01) |
| Cross-field invariant (values already in hand) | `domain/service` (pure) | No | Route origin ≠ destination (BR-07), `RouteValidator` |
| Referential/existence | `domain/service` + `port.out` (existing repository interface) | Yes | Country code must exist in `countries` (BR-04) |
| HTTP payload shape | `adapter-http` | No | JSON field present, correct type |
| Last-resort enforcement | `infrastructure` (DB constraint) | N/A (DB engine) | FK constraint as safety net |

---

## 6. Implementation comparison: "scala 3 opaque type" vs "zio prelude newtype type"

§2.2's `CountryCode.from` is one way to implement the B1 smart constructor.
This section implements the same syntactic check a second way, using
[ZIO Prelude](https://zio.dev/zio-prelude/)'s `Newtype`, and compares the
two — code size, integration effort, pros/cons — to decide which one this
project should actually use. API confirmed against ZIO Prelude's official
docs (`docs/newtypes/index.md`) via Context7, per `CLAUDE.md`'s
"Documentation sources" policy; current stable release is `1.0.0-RC47`.

### 6.1 "scala 3 opaque type" — current implementation

```scala
// domain/src/main/scala/dev/cmartin/aerohex/domain/model/Country.scala
opaque type CountryCode = String

object CountryCode:
  def apply(value: String): CountryCode        = value           // no validation
  def from(value: String): Option[CountryCode] =                 // validating, currently unused
    Option.when(value.length == 2 && value.forall(_.isLetter))(value)
  extension (c: CountryCode) def value: String = c
```

A native Scala 3 language feature — no library, no new dependency. Fully
erased at compile time (identical bytecode to using `String` directly).
`from` is hand-rolled: there is no shared convention pushing every value
object toward a validating constructor, which is exactly how `CountryCode.from`
ended up dead code while `apply` (no validation) is what every call site
actually uses.

### 6.2 "zio prelude newtype type" — same check, ZIO Prelude's `Newtype`

```scala
// domain/src/main/scala/dev/cmartin/aerohex/domain/model/Country.scala
import zio.prelude.Newtype
import zio.prelude.Assertion.*

object CountryCode extends Newtype[String]:
  override inline def assertion = matches("^[A-Za-z]{2}$".r)
type CountryCode = CountryCode.Type
```

```scala
// call sites
val code: CountryCode = CountryCode("ES")                     // compile-time literal check
val validated: Validation[String, CountryCode] = CountryCode.make(rawInput)  // runtime
val raw: String = CountryCode.unwrap(code)                    // no .value extension needed
```

`Newtype` (not `Subtype`) is the right pick here — `Subtype` additionally
exposes the underlying type's own operators (`SequenceNumber(2) > SequenceNumber(1)`),
which `CountryCode` never needs since it's only ever compared for equality.

### 6.3 Code size

| | LOC (type + constructor) | New dependency | `.value`-style accessor |
|---|---|---|---|
| `opaque type` | 5 | none | free — the `extension` method is hand-written but zero-cost |
| `Newtype` | 4–5 (+1 if `.value` postfix style is kept, since `unwrap` is a prefix companion call, not a postfix extension by default) | `"dev.zio" %% "zio-prelude" % "1.0.0-RC47"` | not free — `CountryCode.unwrap(c)` unless you add your own extension to match project convention |

Roughly a wash for one type in isolation. The gap opens up **per additional
value object**: every new `opaque type` needs its own bespoke validation
function written by hand (as `IataCode`/`IcaoCode`/`Registration` all do
today, none of them validating); every new `Newtype` gets `make`/`makeAll`/
`unsafeMake`/`wrap`/`unwrap` for free from the base trait — the boilerplate
that's currently duplicated (or, in practice, simply skipped) per type
becomes a one-line `assertion` override.

### 6.4 Integration effort

- **New dependency**: `zio-prelude` isn't in `project/Versions.scala` today.
  It's still pre-GA (`1.0.0-RC47`) — the same versioning-policy exception
  class as Doobie under `CLAUDE.md`'s "stable GA by default" rule, so
  adopting it means explicitly carving out a second named exception.
- **Return type mismatch**: `CountryCode.make` returns
  `Validation[String, CountryCode]`, not this project's `IO[DomainError, X]`
  or `Option[CountryCode]`. Every call site needs a bridge, e.g.:
  ```scala
  ZIO.fromEither(CountryCode.make(rawCode).toEither)
    .mapError(errors => DomainError.InvalidCountryCode(errors.head))
  ```
  This bridge doesn't exist anywhere in the codebase today — `Validation`
  isn't currently used by any module.
- **ZIO version compatibility**: ZIO Prelude tracks the ZIO 2.x line; no
  conflict expected against this project's `zio = "2.1.26"`, but it's an
  extra artifact to keep in sync on every ZIO bump going forward.
- **Uniformity**: `IataCode`, `IcaoCode`, and `Registration` are all plain
  `opaque type`s today. Introducing `Newtype` for just `CountryCode` mixes
  two value-object idioms in the same module; the honest integration cost
  isn't "add one `Newtype`", it's "migrate all four (or explicitly accept
  the inconsistency)."

### 6.5 Pros and cons

| | Pros | Cons |
|---|---|---|
| **scala 3 opaque type** | Zero dependency; zero runtime cost; already the codebase's established idiom (all 4 value objects); full control over the validation function's return type (could return `IO[DomainError, _]` directly, no bridge needed) | No shared smart-constructor convention — every type reinvents `from`, and nothing stops a second `apply` from bypassing it entirely (the exact bug this doc exists to fix); no compile-time literal checking; no built-in error accumulation across multiple fields |
| **zio prelude newtype type** | Standardized `make`/`makeAll`/`unsafeMake` API across every value object — no bespoke `from` per type; compile-time rejection of invalid *literals* (`CountryCode("E")` fails to compile, not just to validate); `Validation` composes to accumulate errors across multiple fields in one request (e.g. reporting bad `code` *and* bad `name` together) — no equivalent exists today | New pre-GA dependency; `Validation` → `IO[DomainError, _]` bridge needed at every call site, adding a translation layer that doesn't exist elsewhere; mixes idioms with the other 3 value objects unless the whole domain module migrates; the *validation logic itself* (a 2-letter regex) isn't any more expressive than `Option.when(...)` — the win is entirely in the surrounding machinery, not the check |

### 6.6 Recommendation

Stay with the **scala 3 opaque type** for `CountryCode` (and `IataCode`/
`IcaoCode`/`Registration`) for now: this project already has zero
dependencies for value objects, `CLAUDE.md`'s versioning policy already
grants exactly one pre-GA exception (Doobie) and a second one isn't
justified by a single 2-letter regex check, and §2's actual gap — `from`
being dead code — is a *call-site* problem (nothing routes through it), not
a *capability* problem that `Newtype` would fix on its own. Revisit ZIO
Prelude if a real need for cross-field accumulated validation shows up
(e.g. a `CreateCountryRequest` that should report every invalid field at
once instead of failing on the first one) — that's the one capability the
opaque-type approach cannot cheaply replicate.
