# Domain entity review/hardening progress

Tracks, per domain entity, how far each has been carried across the full stack — docs,
scaladoc, layer-consistency passes, persistence wiring, HTTP surface, tests, and schema. No
such cross-cutting tracker existed before this file (see "Why this file exists" below); update
it whenever a review/hardening pass touches an entity, rather than letting the info live only
in commit history or scattered plan docs.

## Status table

Snapshot as of 2026-07-11 (updated: Aircraft full CRUD + persistence completed).

| Entity | Docs (`01-domain-model.md`) | Scaladoc | Layer consistency | Persistence wiring | HTTP layer | Unit tests | Integration tests (Quill+Doobie) | True endpoint→DB E2E | Migration/schema |
|---|---|---|---|---|---|---|---|---|---|
| **Country** | ✓ full, fresh citations | ✓ | ✓ refactored; minor gap: `CountryCode.from` validator is dead code | ✓ Quill wired | ✓ full CRUD, validated | ✓ both layers | ✓ both adapters | ✓ Postman "E2E — Country" folders | ✓ V1, V7 |
| **Airport** | ✓ full, fresh | ✓ | ✓ refactored; BR-12 pagination-validator gap closed | ✓ Quill wired | ✓ full CRUD, validated (DELETE added, fails on not-found for both Quill+Doobie) | ✓ both layers | ✓ both adapters, incl. delete-not-found case | ✓ Postman "E2E — Airport CRUD lifecycle" folder | ✓ V2/V6/V7/V8 |
| **Airline** | ✓ full, fresh (BR-03/BR-14/Open Questions #6-7 resolved) | ✓ | ✓ shares `IcaoCode` with Airport, same relationship-only Country pattern | ✓ Quill wired, full CRUD | ✓ full CRUD, validated (CREATE/UPDATE/DELETE added; Doobie `save` fixed to fail on duplicate instead of silently upserting) | ✓ both layers | ✓ both adapters, incl. not-found/duplicate cases | ✓ Postman "E2E — Airline CRUD lifecycle" folder | ✓ V3/V9/V10 (V9 was a late fix) |
| **Aircraft** | ✓ full, fresh (BR-04/05/14 extended, new BR-15, Open Question #5 split) | ✓ | ✓ FK embedded directly on the entity (`airlineIcao` field), unlike Airport/Airline's separate-parameter pattern | ✓ Quill wired, full CRUD (new — was in-memory stub) | ✓ full CRUD, validated (registration: non-blank + max 10 chars only, no shape pattern — deliberate, see BR-15) | ✓ both layers | ✓ both adapters, incl. not-found/duplicate/FK cases, two-level Country→Airline→Aircraft seeding | ✓ Postman "E2E — Aircraft CRUD lifecycle" folder | ✓ V11 (new table, surrogate id from creation) |
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
