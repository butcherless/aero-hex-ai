# Validation in Hexagonal Architecture — Analysis

> **Project:** aero-hex-ai
> **Example used:** `Country(code, name)` — a 2-letter code that must be both
> syntactically valid (2 letters) and referentially valid (must exist in the
> `countries` table)
> **Decision:** split validation across `domain/model` (syntax) and
> `domain/port/out` (existence). See §2.
> **Outcome (shipped):** `CountryCode`'s syntax check is real — implemented
> as a ZIO Prelude `Newtype`, not the plain opaque type + `Option`-returning
> `from` this doc originally sketched in §2.2. §6 compares the two; §6.6's
> own recommendation was to *not* do this — the actual decision overrode it.
> See §2.2/§2.4a for what's live today. The same `Newtype` pattern was later
> extended from `CountryCode` to `IataCode`, `IcaoCode`, and `Registration` —
> each entity's own natural-key field gets the same real, enforced syntax
> check on its create path; see §4's table for exactly what shipped per type.
> None of the three gained an existence check (§2.3/§2.4's "B2"/"B3") —
> only `CountryCode` has one, via the different, standalone mechanism in
> §2.4a.

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

### 2.1 Current state in aero-hex-ai

- **Syntax (BR-01, `Country.code` itself)**: `[SHIPPED]` — see §2.2. `CountryCode`
  is a ZIO Prelude `Newtype[String]` whose `assertion` is the 2-letter/alpha
  check; `CreateCountryRequest.toCommand` (`adapter-http/.../dto/CountryDto.scala`)
  calls `CountryCode.make(req.code).toZIO`, failing fast with
  `DomainError.InvalidCountryCode` before `CreateCountryService` is ever
  reached. The Tapir `Validator.pattern` that used to do this at the HTTP
  boundary was removed once this landed — domain is now the one source of
  truth for the shape, not a second copy of it.
- **Existence, `Country`'s own code (BR-16, is this a *real* ISO code)**:
  `[SHIPPED]` — see §2.4a. `CountryRepository.validateCode`, backed by a
  standalone `country_codes` reference table.
- **Existence, a *referenced* parent (BR-04 — an Airport's/Airline's Country,
  or an Aircraft's Airline, must already exist)**: still **not** a dedicated
  `domain/service`. It's embedded inside the **persistence adapter**:
  `QuillCountryIdResolver.resolveCountryId`
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
  the parent to the repository. §2.3/§2.4/§3's `domain/service` blueprint for
  *this* case remains unimplemented — see §2.4a's closing note.

### 2.2 B1 (as shipped) — `CountryCode`: Value Object, syntactic only, ZIO Prelude smart constructor

This section originally sketched activating the opaque type's unused
`Option`-returning `from` method. What actually shipped instead — see §6 for
the full comparison against that original plan:

```scala
// domain/src/main/scala/dev/cmartin/aerohex/domain/model/Country.scala
package dev.cmartin.aerohex.domain.model

import zio.prelude.Newtype
import zio.prelude.Assertion.*

object CountryCode extends Newtype[String]:
  override inline def assertion = matches("^[a-zA-Z]{2}$".r)
  extension (c: CountryCode) def value: String = unwrap(c)
  def unsafeMake(value: String): CountryCode = wrap(value)
type CountryCode = CountryCode.Type
```

Pure, total, zero I/O, trivially unit-testable — same properties the
original `from` plan aimed for. Answers only: *"is this string shaped like a
country code?"* The one call site that needs runtime validation:

```scala
// adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/dto/CountryDto.scala
def toCommand(req: CreateCountryRequest): IO[DomainError, CreateCountryCommand] =
  CountryCode.make(req.code).toZIO
    .orElseFail(DomainError.InvalidCountryCode(req.code))
    .map(CreateCountryCommand(_, req.name))
```

Every other construction site in the codebase (DB reads, HTTP path params
already Tapir-validated, `Update`/`Delete` flows) uses `CountryCode.unsafeMake`
instead — deliberately unvalidated, because the string is already trusted by
the time it gets there. `InvalidCountryCode(code: String)` was added to
`domain/error/DomainError.scala`, following the one precedent that existed
for a validation-shaped error, `InvalidRoute(reason: String)` — and is now
shared by *both* this check and §2.4a's membership check, since both mean
the same thing to a caller ("this isn't a valid country code").

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
on `CountryRepository` (`validateCode`), called inline from
`CreateCountryService.create`, no separate `domain/service` class. The
method returns `IO[DomainError, Unit]`, not a `Boolean` — an earlier draft of
this method (`isValidCode: IO[DomainError, Boolean]`) made every caller
pattern-match and construct `InvalidCountryCode` itself; `validateCode`
constructs it once, in the one place that actually knows what "invalid"
means for this table, matching how `save`/`update`/`delete` on this same
class already own constructing `CountryAlreadyExists`/`CountryNotFound`:

```scala
// domain/src/main/scala/dev/cmartin/aerohex/domain/port/out/CountryRepository.scala
trait CountryRepository {
  def validateCode(code: CountryCode): IO[DomainError, Unit]
  // ...
}
```

```scala
// application/src/main/scala/dev/cmartin/aerohex/application/service/CreateCountryService.scala
override def create(command: CreateCountryCommand): IO[DomainError, Country] =
  val effect =
    repo.validateCode(command.code) *>
      repo.findByCode(command.code).flatMap:
        case Some(_) => ZIO.fail(DomainError.CountryAlreadyExists(command.code.value))
        case None    => repo.save(Country(command.code, command.name))
  effect @@ ServiceAspect.logged(s"CreateCountryService.create(${command.code.value})")
```

This and §2.2's shape check are **not** redundant, and — unlike the original
draft of this section — both are now actually live simultaneously, checking
different things: `CountryCode.make`'s assertion rejects `"123"` or `"E"`
before a `CreateCountryCommand` can even be constructed; `validateCode` only
rejects *real-shaped* codes that aren't *real* codes (`"ZZ"`, `"XX"`). Both
funnel into the same `DomainError.InvalidCountryCode` → 400, so a caller sees
one consistent error shape regardless of which check actually fired.

§2.3/§2.4/§3's original subject — an Airport/Airline/Aircraft validating that
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

domain/model/      → CountryCode's Newtype assertion: 2-letter/alpha format
                     (pure, total, no I/O) — this is BR-01: "country code
                     format". [SHIPPED] — see §2.2/§6.

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
| `CountryCode` | `domain/model/Country.scala` | `[SHIPPED]` 2 letters, alphabetic (BR-01), real `Newtype` assertion, wired into `CreateCountryRequest.toCommand` | exists in `countries` — **not this pattern**; see §2.4a (`CountryRepository.validateCode` against a standalone `country_codes` table is a different, ISO-*membership* check, BR-16, not "does a row already exist") |
| `IataCode` | `domain/model/Airport.scala` | `[SHIPPED]` 3 letters, alphabetic (BR-02), real `Newtype` assertion, wired into `CreateAirportRequest.toCommand` (Airport's own `iataCode` field only — `Route.origin`/`destination` still `unsafeMake`) | not implemented — `Route`'s referenced airports are validated via `FindAirportUseCase.findByIata` in `CreateRouteService` (BR-09), not a dedicated `domain/service` |
| `IcaoCode` | `domain/model/Airline.scala` (shared with `Airport`/`Route`/`Flight`/`Aircraft`) | `[SHIPPED, partial]` alphabetic only, deliberately **no length** in the `Newtype` itself (Airport's own `icaoCode` is 4 letters, Airline's own `icao` is 3 — the two owning entities disagree, so length stays an HTTP-layer `Validator`, BR-03). Real `Newtype` assertion wired into both `CreateAirportRequest.toCommand` and `CreateAirlineRequest.toCommand`; every cross-entity reference field (`Route`/`Flight`/`Aircraft`'s `airlineIcao`) still `unsafeMake` | not implemented — no referential check exists anywhere `IcaoCode` is used as a reference (`Aircraft`'s `airlineIcao` only gets DB-level FK enforcement, not a domain-layer check) |
| `Registration` | `domain/model/Aircraft.scala` | `[SHIPPED]` non-blank, ≤ 10 chars, deliberately no shape pattern (BR-15 — real-world registrations vary by country), real `Newtype` assertion wired into `CreateAircraftRequest.toCommand` — bound-for-bound identical to the HTTP `Validator` already there, so there's no input the domain check rejects that Tapir doesn't already reject | n/a — `Registration` is never used as a reference field elsewhere |

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
| Format/shape | `domain/model` (smart constructor) | No | `CountryCode`'s `Newtype` assertion, 2 letters (BR-01) |
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

### 6.1 "scala 3 opaque type" — the implementation before this decision

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

### 6.2 "zio prelude newtype type" — `[SHIPPED]`, what's actually live now

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
  `Validation[String, CountryCode]`, not this project's `IO[DomainError, X]`.
  The one real call site (`CreateCountryRequest.toCommand`) bridges it with
  `Validation`'s own `.toZIO: IO[E, A]` — simpler than the `.toEither` bridge
  this section originally guessed at, and doesn't need a `NonEmptyChunk`
  unwrap:
  ```scala
  CountryCode.make(req.code).toZIO
    .orElseFail(DomainError.InvalidCountryCode(req.code))
  ```
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

### 6.6 Recommendation (original) and outcome (actual)

**Original recommendation:** stay with the **scala 3 opaque type** for
`CountryCode` (and `IataCode`/`IcaoCode`/`Registration`) for now: this
project already has zero dependencies for value objects, `CLAUDE.md`'s
versioning policy already grants exactly one pre-GA exception (Doobie) and a
second one isn't justified by a single 2-letter regex check, and §2's actual
gap — `from` being dead code — is a *call-site* problem (nothing routes
through it), not a *capability* problem that `Newtype` would fix on its own.

**`[OVERRIDDEN]` — what actually shipped:** `CountryCode` was rewritten as a
ZIO Prelude `Newtype` anyway, as an explicit decision, not a re-derivation of
this recommendation — this section's own tradeoffs (§6.4's integration cost,
§6.5's cons column) still hold and were accepted knowingly:

- `project/Versions.scala` now carries a second pre-GA exception
  (`zioPrelude = "1.0.0-RC47"`), alongside Doobie, under `CLAUDE.md`'s
  versioning policy.
- `IataCode`/`IcaoCode`/`Registration` were **not** migrated — `CountryCode`
  is the only `Newtype` in the domain module, so the "uniformity" con in
  §6.4/§6.5 is real and current, not hypothetical. Whether that's temporary
  (a migration in progress) or permanent (a deliberate single exception) is
  unresolved — flagged here rather than in a new Open Question, since it's a
  direct continuation of this section's own analysis.
- The cross-field accumulated-validation capability this section named as
  the one thing worth revisiting for (§6.6 original) is **still unused** —
  `CreateCountryRequest.toCommand` only validates `code`, still fails fast
  on the first problem rather than accumulating `code` and `name` errors
  together. Adopting `Newtype` didn't happen because that need showed up; it
  didn't yet.
