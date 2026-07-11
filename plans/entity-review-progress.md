# Domain entity review/hardening progress

Tracks, per domain entity, how far each has been carried across the full stack — docs,
scaladoc, layer-consistency passes, persistence wiring, HTTP surface, tests, and schema. No
such cross-cutting tracker existed before this file (see "Why this file exists" below); update
it whenever a review/hardening pass touches an entity, rather than letting the info live only
in commit history or scattered plan docs.

## Status table

Snapshot as of 2026-07-12 (updated: `CountryCode`'s ZIO Prelude `Newtype` pattern extended to
`IataCode`/`IcaoCode`/`Registration`, each with real `.make`/`.toZIO` validation wired into its
owning entity's create path).

| Entity | Docs (`01-domain-model.md`) | Scaladoc | Layer consistency | Persistence wiring | HTTP layer | Unit tests | Integration tests (Quill+Doobie) | True endpoint→DB E2E | Migration/schema |
|---|---|---|---|---|---|---|---|---|---|
| **Country** | ✓ full, fresh citations (new BR-16) | ✓ | ✓ `CountryCode` rewritten as a ZIO Prelude `Newtype` — BR-01 (shape) is now a real, enforced smart constructor, not dead code; BR-16 (ISO membership) is a separate, also-live check via `CountryRepository.validateCode`, see `docs/analysis/validation-analysis-hexagonal.md` §2.2/§2.4a/§6 | ✓ Quill wired | ✓ full CRUD, validated; create rejects malformed codes (`CountryCode.make`) and non-ISO codes (`validateCode`), both → `InvalidCountryCode` (400) | ✓ both layers | ✓ both adapters, incl. `validateCode` success/failure cases | ✓ Postman "E2E — Country" folders | ✓ V1, V7, V12 (`country_codes` master table) |
| **Airport** | ✓ full, fresh | ✓ | ✓ refactored; BR-12 pagination-validator gap closed; `IataCode`/`IcaoCode` rewritten as ZIO Prelude `Newtype`s — both of Airport's own fields (`iataCode` BR-02, `icaoCode` BR-03's shape half) are real, enforced smart constructors on create now, not dead code | ✓ Quill wired | ✓ full CRUD, validated (DELETE added, fails on not-found for both Quill+Doobie); create rejects malformed `iata`/`icaoCode` (`IataCode.make`/`IcaoCode.make`) → `InvalidIataCode`/`InvalidIcaoCode` (400); redundant Tapir pattern validators removed from the create DTO (kept `minLength`/`maxLength`) | ✓ both layers, incl. real-check-not-a-stub cases for `iata`/`icaoCode` | ✓ both adapters, incl. delete-not-found case | ✓ Postman "E2E — Airport CRUD lifecycle" folder | ✓ V2/V6/V7/V8 |
| **Airline** | ✓ full, fresh (BR-03/BR-14/Open Questions #6-7 resolved) | ✓ | ✓ shares `IcaoCode` with Airport, same relationship-only Country pattern; `IcaoCode` rewritten as a ZIO Prelude `Newtype` — Airline's own `icao` field is a real, enforced smart constructor on create now (alphabetic shape only, since `IcaoCode` is length-agnostic across Airline/Airport) | ✓ Quill wired, full CRUD | ✓ full CRUD, validated (CREATE/UPDATE/DELETE added; Doobie `save` fixed to fail on duplicate instead of silently upserting); create rejects malformed `icao` (`IcaoCode.make`) → `InvalidIcaoCode` (400); redundant Tapir pattern validator removed from the create DTO (kept `minLength`/`maxLength`) | ✓ both layers, incl. a real-check-not-a-stub case for `icao` | ✓ both adapters, incl. not-found/duplicate cases | ✓ Postman "E2E — Airline CRUD lifecycle" folder | ✓ V3/V9/V10 (V9 was a late fix) |
| **Aircraft** | ✓ full, fresh (BR-04/05/14 extended, new BR-15, Open Question #5 split) | ✓ | ✓ FK embedded directly on the entity (`airlineIcao` field, constructed via `IcaoCode.unsafeMake` — a cross-entity reference, not Aircraft's own key), unlike Airport/Airline's separate-parameter pattern; `Registration` rewritten as a ZIO Prelude `Newtype` (non-blank + ≤10 chars, bound-identical to the HTTP `Validator` already there — see BR-15) | ✓ Quill wired, full CRUD (new — was in-memory stub) | ✓ full CRUD, validated (registration: non-blank + max 10 chars only, no shape pattern — deliberate, see BR-15); create rejects malformed `registration` (`Registration.make`) → `InvalidRegistration` (400) | ✓ both layers | ✓ both adapters, incl. not-found/duplicate/FK cases, two-level Country→Airline→Aircraft seeding | ✓ Postman "E2E — Aircraft CRUD lifecycle" folder | ✓ V11 (new table, surrogate id from creation) |
| **Route** | ✓ (BR-10 flagged `[MISSING]`) | ✗ none | ✗ none | ✗ **in-memory stub** | create-only, **no GET at all** | ✗ no service spec; ✓ endpoint spec | ✗ **none** (Doobie repo exists, untested) | ✗ none | ✓ V4/V7 |
| **Flight** | ✓ (redundant `airlineIcao` flagged, unresolved) | ✗ none | ✗ none | ✗ in-memory stub | find-only, **no Validator** | ✗ no service spec; ✓ endpoint spec | ✗ **no repo exists** | ✗ none | ✗ **no table — `[MISSING]`** |
| **FlightInstance** | ✓ (stub-only) | ✗ none | ✗ none | ✗ in-memory stub | find-only, **has** a UUID `Validator` | ✗ no service spec; ✓ endpoint spec | ✗ **no repo exists** | ✗ none | ✗ **no table — `[MISSING]`** |

Legend: ✓ done · ✗ not done · partial = started but incomplete.

## Summary judgment

- **Fully hardened:** **Country**, **Airport**, **Airline**, and **Aircraft** — all four have
  real persistence, full CRUD, scaladoc, both unit-test layers, both Quill+Doobie integration
  tests, and true HTTP-level E2E scenarios. Airport's DELETE endpoint was completed
  2026-07-11. Airline's full CRUD was also completed 2026-07-11 — this also fixed a real bug
  where `DoobieAirlineRepository.save` silently upserted on a duplicate ICAO code instead of
  failing with `AirlineAlreadyExists` like the wired Quill adapter. Aircraft's hardening
  (2026-07-11) was the largest of the three since, unlike Airline, it started with **no**
  Flyway schema and **no** persistence adapter of any kind: added `V11__create_aircraft.sql`
  (surrogate id from creation), new `QuillAircraftRepository`/`DoobieAircraftRepository`
  (join-based reads since `airlineIcao` is a field embedded directly on the entity, not a
  separate parameter), `CreateAircraftUseCase`/`UpdateAircraftUseCase`/`DeleteAircraftUseCase`,
  a new `AircraftAlreadyExists` error case, HTTP CRUD endpoints with a deliberately
  format-agnostic registration validator (non-blank + max 10 chars — real-world registrations
  vary by country, unlike the fixed-length IATA/ICAO codes elsewhere), a new `description`
  field (common/marketing name distinct from the coded `typeCode`), service+endpoint unit
  tests, an `AircraftRepositoryContractSpec` integration suite seeding a two-level
  Country→Airline→Aircraft dependency chain, and a Postman E2E lifecycle folder.
- **Newtype migration (2026-07-12):** `CountryCode`'s ZIO Prelude `Newtype` pattern — a real,
  enforced `assertion` at construction instead of a validating-but-unused `apply` — was extended
  from Country to `IataCode` (Airport), `IcaoCode` (Airline, shared with Airport/Route/Flight/
  Aircraft), and `Registration` (Aircraft). Each of the four now-Newtype value objects gets a
  real `.make(...).toZIO` check wired into its *owning* entity's create path only
  (`CreateAirportRequest`/`CreateAirlineRequest`/`CreateAircraftRequest.toCommand`, mirroring
  `CreateCountryRequest`); every cross-entity reference field (`Route.origin`/`destination`,
  `Route`/`Flight`/`Aircraft`'s `airlineIcao`) and every update path still construct via
  `unsafeMake`, unchanged. Added `DomainError.InvalidIataCode`/`InvalidIcaoCode`/
  `InvalidRegistration`, each mapped to HTTP 400 in `ErrorMapper`. Where the domain check made a
  Tapir-level `Validator.pattern` fully redundant (`IataCode`/`IcaoCode`), that pattern was
  removed from the create DTO's schema (kept `minLength`/`maxLength`), matching the precedent
  `CountryCode` set — `Registration` had no pattern validator to remove (see BR-15). New
  endpoint-spec tests prove each real check fires independently of the (now-loosened) Tapir
  schema validator, the same way `CountryEndpointsSpec` already proved it for `CountryCode`.
  `Aircraft.airlineIcao` was also, separately, removed and then restored to its original
  FK-on-the-entity design within this same work — net no change to that relationship, just its
  construction now goes through `IcaoCode.unsafeMake` instead of a plain opaque-type `apply`.
- **Partially reviewed:** **Route** (richly documented gaps, endpoint spec exists, but
  persistence is a stub and it has zero integration tests despite a Doobie repo existing).
- **Not yet reviewed:** **Flight, FlightInstance** — no scaladoc, no persistence repos of any
  kind (Quill or Doobie), no schema/migration, no service-level tests, and (for Flight) no HTTP
  path-param validation.

## Why this file exists

Before this file, no document combined all 8 review dimensions above across all 7 entities.
The closest candidates, and what each actually covers instead:

- `docs/analysis-plan.md` — a one-time task list that drove the analysis docs; only
  partially executed (the use-cases and ADR docs it calls for were never written).
- `docs/analysis/01-domain-model.md` — the richest source (business rules with
  `[MISSING]`/`[ASSUMPTION]` tags, open questions), but organized by rule/entity definition,
  not as a cross-cutting checklist.
- `docs/api/endpoint-status.md` — tracks HTTP endpoint implementation status only
  (implemented vs. stub), nothing else.

## Updating this file

When a review/hardening pass touches an entity (adds scaladoc, wires persistence, adds
integration tests, etc.), update that entity's row directly rather than appending a changelog
— this file should always reflect current state, not history (git blame covers that).
