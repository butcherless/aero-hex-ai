# Plan: Refactor Country/Airport test suites to remove duplication

## Goal

Reduce copy-pasted boilerplate across the four existing Country/Airport spec files
without changing test behavior — same assertions, same test names, same 70/70 passing
count, just less repetition. Extract repeated literals to named constants and repeated
structural patterns into helper objects.

## Scope

```
application/src/test/scala/dev/cmartin/aerohex/application/service/CountryServiceSpec.scala   (199 lines)
application/src/test/scala/dev/cmartin/aerohex/application/service/AirportServiceSpec.scala    (285 lines)
adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/CountryEndpointsSpec.scala (236 lines)
adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/AirportEndpointsSpec.scala (296 lines)
```

`application/src/test/.../aspect/ServiceAspectSpec.scala` is small (17 lines) and has no
duplication worth touching — out of scope.

## Current state (confirmed via Explore)

No test-support/fixture/helper convention exists anywhere in the repo today — every spec
file is fully self-contained, and no module currently has a cross-module test dependency
(no test-jar, no `"test->test"` config). `zio-test`/`zio-test-sbt` are wired globally via
root-level `commonTest`; `adapter-http` additionally has `tapir-sttp-stub4-server` and
`sttp-client4-zio` in `Test` scope. `shared-kernel` has no test sources.

## Duplication inventory

### 1. (Biggest win) Repository stub boilerplate — `application` module

`CountryServiceSpec` and `AirportServiceSpec` each define a full `CountryRepository`/
`AirportRepository` anonymous instance **per test**, re-declaring every one of its 6
(Country) or 7 (Airport) methods, where all but 1–2 of them just delegate to a shared
`unimplemented` stub. Concretely:

- `CountryServiceSpec`: 8 separate `new CountryRepository: ...` blocks (6 methods each,
  ~7 lines per block including delegation lines) — roughly **100 of the file's 199
  lines** are this repeated shape, differing only in which 1–2 methods are overridden.
- `AirportServiceSpec`: 9 separate `new AirportRepository: ...` blocks (7 methods each)
  — an even larger share of its 285 lines, same pattern.

Example of the repeated shape (from `CountryServiceSpec`, one of ~8 near-identical blocks):

```scala
val repo = new CountryRepository:
  def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.some(spain)
  def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
  def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
  def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
  def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
  def delete(code: CountryCode): IO[DomainError, Unit]                = unimplemented.delete(code)
```

Only the `findByCode` line actually matters to this test; the other 5 lines are pure
copy-paste, repeated with trivial variation 8 times in this file alone.

### 2. `.layer` construction tests — `application` module

7 near-identical blocks (4 in `CountryServiceSpec`, 3 in `AirportServiceSpec`) of the shape:

```scala
test("XxxService.layer constructs a usable instance") {
  for svc <- ZIO.service[XxxUseCase].provide(ZLayer.succeed(dep), XxxService.layer)
  yield assertTrue(svc != null)
}
```

### 3. Domain fixture values duplicated *across* modules

`private val spain = Country(CountryCode("ES"), "Spain")` is defined identically in both
`CountryServiceSpec.scala` and `CountryEndpointsSpec.scala`. Same for `germany`, and for
`madrid`/`barcelona` between `AirportServiceSpec.scala` and `AirportEndpointsSpec.scala`.
This is real duplication, but it's small (2–3 `val`s per pair) and crosses a module
boundary (`application` vs `adapter-http`) — see the design decision below.

### 4. HTTP-layer repetition — `adapter-http` module

- **Base URI**: `uri"https://test.com/api/v1/countries..."` / `.../airports...` repeated
  in nearly every one of the ~40 tests across both files, only the path suffix varies.
- **Response decoding**: `decode[List[XxxDto]](response.body.merge).getOrElse(Nil)` and
  `decode[XxxDto](response.body.merge).toOption` repeated verbatim (varying only the
  type parameter) in the majority of tests that inspect a response body.
- **Location-header assertion**: `response.headers.exists(h => h.name.equalsIgnoreCase("Location") && h.value.contains(X))`
  duplicated between the Country and Airport `POST` "returns 201" tests.
- **JSON request bodies**: some exact bodies repeat verbatim across tests (e.g. the valid
  create-Airport JSON), though many intentionally vary by one field to test a specific
  validator — those should generally **stay inline**, not be over-abstracted, since the
  body *is* the thing under test in a validation-rejection case.
- The use-case stub pattern (`defaultFind`/`notFoundFind`/`conflictCreate`/etc.) has the
  same "1–2 methods matter, rest are boilerplate" shape as item 1, but the use-case
  interfaces here are much smaller (2–4 methods vs. 6–7 for repositories), so the payoff
  from extracting it is smaller. Still worth doing with the same technique for consistency.

## Design decisions to confirm before implementing

1. **Repository-stub extraction technique.** Recommend a factory function taking
   per-method overrides as **named parameters with defaults that fall back to a
   `NotImplementedError` die**, mirroring the technique already used for `makeBackend`
   in both HTTP specs (default use-case stubs, override just what a test needs) — this
   is a proven, in-codebase-precedented pattern, not a new idiom:

   ```scala
   object CountryRepositoryStub:
     private def unimplemented[A](name: String): IO[DomainError, A] =
       ZIO.die(new NotImplementedError(name))

     def apply(
         findByCode: CountryCode => IO[DomainError, Option[Country]] = _ => unimplemented("findByCode"),
         findAll: Pagination => UIO[List[Country]] = _ => ZIO.die(new NotImplementedError("findAll")),
         searchByName: String => UIO[List[Country]] = _ => ZIO.die(new NotImplementedError("searchByName")),
         save: Country => IO[DomainError, Country] = _ => unimplemented("save"),
         update: Country => IO[DomainError, Country] = _ => unimplemented("update"),
         delete: CountryCode => IO[DomainError, Unit] = _ => unimplemented("delete")
     ): CountryRepository = new CountryRepository:
       def findByCode(code: CountryCode)   = findByCode(code)
       def findAll(p: Pagination)          = findAll(p)
       def searchByName(q: String)         = searchByName(q)
       def save(c: Country)                = save(c)
       def update(c: Country)              = update(c)
       def delete(code: CountryCode)       = delete(code)
   ```

   Each of the ~8 call sites collapses from ~7 lines to 1:
   ```scala
   val repo = CountryRepositoryStub(findByCode = _ => ZIO.some(spain))
   ```
   and the "capture what was written" tests (currently using a `Ref`) keep working
   unchanged, just passing a lambda that also updates the `Ref`:
   ```scala
   val repo = CountryRepositoryStub(findByCode = _ => ZIO.none, save = c => savedRef.set(Some(c)).as(c))
   ```
   Same technique for `AirportRepositoryStub`. Estimated reduction: ~100 lines →
   ~15–20 in `CountryServiceSpec`; a comparable or larger cut in `AirportServiceSpec`
   (7-method interface, more call sites).

2. **Where do these stub-builder objects live?** Recommend
   `application/src/test/scala/dev/cmartin/aerohex/application/testsupport/CountryRepositoryStub.scala`
   and `.../testsupport/AirportRepositoryStub.scala` — one file per resource, matching
   the existing one-file-per-resource convention (`CountryDto`/`AirportDto`,
   `CountryEndpoints`/`AirportEndpoints`, etc.). No build.sbt change needed — both specs
   already live in the same module/source-set.

3. **Cross-module fixture duplication (`spain`, `madrid`, `barcelona`) — accept it or
   build shared infrastructure?** Two options:
   - **(Recommended) Accept the duplication.** It's 2–3 `val` declarations per pair,
     stable data that essentially never changes, and eliminating it would require a new
     shared module (e.g. `test-kit`, depending on `domain`, with `application` and
     `adapter-http` both adding a `Test`-scoped dependency on it) — real build
     complexity for a very small payoff in a demo/teaching-scoped project.
   - **(Alternative) Introduce a shared `test-kit` module.** Only worth it if more
     resources (Airline, Aircraft, Flight, FlightInstance) get test suites later and the
     fixture-duplication problem grows across many more file pairs. Flag as a future
     revisit point, not something to do now.

4. **`.layer` test helper — worth adding?** A generic helper can only trivially
   simplify the *single-dependency* layer tests (6 of the 7: everything except
   `FindAirportsByCountryService.layer`, which takes two repositories). Recommend:
   ```scala
   def layerResolves[R: Tag](layer: ZLayer[Any, Nothing, R]): ZIO[Any, Nothing, TestResult] =
     ZIO.service[R].provide(layer).map(r => assertTrue(r != null))
   ```
   used as `test(...) { layerResolves(ZLayer.succeed(unimplemented) >>> CreateCountryService.layer) }`.
   Modest win (~4 lines → ~1 line × 6 call sites); low risk. Include it, but it's the
   lowest-priority item here — skip first if time-constrained.

5. **HTTP-layer helper object location and scope.** Recommend a single
   `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/testsupport/HttpSpecSupport.scala`
   (resource-agnostic, so one file, not two) providing:
   ```scala
   object HttpSpecSupport:
     val baseUri = uri"https://test.com"

     def decodeList[A: Decoder](response: Response[Either[String, String]]): List[A] =
       decode[List[A]](response.body.merge).getOrElse(Nil)

     def decodeOne[A: Decoder](response: Response[Either[String, String]]): Option[A] =
       decode[A](response.body.merge).toOption

     def hasLocationContaining(response: Response[Either[String, String]], fragment: String): Boolean =
       response.headers.exists(h => h.name.equalsIgnoreCase("Location") && h.value.contains(fragment))
   ```
   **Verify the exact `Response[...]` type parameter against the sttp4 API in use**
   (`sttp-client4`) when implementing — inferred here from `response.body.merge` usage
   in the existing specs, but confirm via Context7/sttp docs rather than assuming, per
   the project's "fetch current docs" convention for library API calls.

   Usage collapses e.g.
   ```scala
   countries = decode[List[CountryDto]](response.body.merge).getOrElse(Nil)
   ```
   to
   ```scala
   countries = HttpSpecSupport.decodeList[CountryDto](response)
   ```
   and the URI construction from
   ```scala
   basicRequest.get(uri"https://test.com/api/v1/countries?name=Spa")
   ```
   to (optional, smaller win, only if it reads better)
   ```scala
   basicRequest.get(uri"${HttpSpecSupport.baseUri}/api/v1/countries?name=Spa")
   ```
   — confirm during implementation whether the interpolated-`baseUri` form is actually
   more readable than the current fully-inline URIs; if not, skip this specific part and
   keep URIs inline (the decode/Location helpers are the real win here, not the base URI).

6. **JSON request bodies — do not over-abstract.** Only extract a body to a named
   constant if it's reused **verbatim** across multiple tests unchanged. Leave
   validation-edge-case bodies (deliberately-wrong field lengths/patterns) inline, since
   the malformed value is the point of the test and a constant would hide it.

## Implementation steps

1. Create `application/src/test/.../testsupport/CountryRepositoryStub.scala` and
   `.../testsupport/AirportRepositoryStub.scala` per the design in decision 1–2.
2. Refactor `CountryServiceSpec.scala` to use `CountryRepositoryStub(...)` at every call
   site; delete the inline `unimplemented` val and all manual `new CountryRepository:` blocks.
3. Refactor `AirportServiceSpec.scala` the same way with `AirportRepositoryStub(...)`
   (and keep its separate `CountryRepositoryStub` usage for the `FindAirportsByCountryService`
   tests, reusing the same stub object rather than redefining `unimplementedCountryRepo` locally).
4. Add the `layerResolves` helper (decision 4) to both specs (or a shared
   `testsupport/LayerSupport.scala` if the exact same helper is used in both — recommend
   the latter to avoid a fresh duplication as soon as this refactor lands).
5. Create `adapter-http/src/test/.../endpoint/testsupport/HttpSpecSupport.scala` per
   decision 5, after confirming the sttp4 `Response` type signature.
6. Refactor `CountryEndpointsSpec.scala` and `AirportEndpointsSpec.scala` to use
   `HttpSpecSupport.decodeList`/`decodeOne`/`hasLocationContaining` at every applicable
   call site.
7. Leave the `spain`/`germany`/`madrid`/`barcelona` fixture duplication in place per
   decision 3, unless the user asks for the shared-module alternative.
8. **After each file's refactor**: run `sbt scalafmtAll && sbt compile` (zero warnings),
   then `sbt "adapterHttp/testOnly *"` / `sbt "application/testOnly *"` and confirm the
   count is still exactly 40 / 30 tests passing with the same names — a refactor that
   changes test *names*, *counts*, or *assertions* has gone out of scope.
9. Do **not** chase coverage numbers as part of this refactor — the goal is structural
   (less duplication), not incremental coverage. If coverage regenerates cleanly as a
   side effect, note the new numbers, but don't force a rebuild-cache workaround for it
   (see the known SBT/scoverage CAS staleness issue already documented from prior
   sessions — orthogonal to this task).

## Acceptance criteria

- `sbt compile` and `sbt scalafmtAll` both clean, zero warnings.
- Exactly 70 tests still pass (30 `application` + 40 `adapter-http`), same test names,
  same suite structure — this refactor changes *how* each test is written, not *what*
  it verifies.
- Net line count materially lower in all four files (rough target: `CountryServiceSpec`
  199 → ~110–130 lines, `AirportServiceSpec` 285 → ~150–180 lines; HTTP specs shrink
  more modestly, maybe 10–15% each, since their duplication is less severe).
- No new cross-module build dependency introduced (per decision 3's recommendation).

## Files touched (summary)

- `application/src/test/scala/dev/cmartin/aerohex/application/testsupport/CountryRepositoryStub.scala` (new)
- `application/src/test/scala/dev/cmartin/aerohex/application/testsupport/AirportRepositoryStub.scala` (new)
- `application/src/test/scala/dev/cmartin/aerohex/application/testsupport/LayerSupport.scala` (new, if decision 4 accepted)
- `application/src/test/scala/dev/cmartin/aerohex/application/service/CountryServiceSpec.scala` (refactored)
- `application/src/test/scala/dev/cmartin/aerohex/application/service/AirportServiceSpec.scala` (refactored)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/testsupport/HttpSpecSupport.scala` (new)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/CountryEndpointsSpec.scala` (refactored)
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/endpoint/AirportEndpointsSpec.scala` (refactored)
