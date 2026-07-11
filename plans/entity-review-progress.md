# Domain entity review/hardening progress

Tracks, per domain entity, how far each has been carried across the full stack — docs,
scaladoc, layer-consistency passes, persistence wiring, HTTP surface, tests, and schema. No
such cross-cutting tracker existed before this file (see "Why this file exists" below); update
it whenever a review/hardening pass touches an entity, rather than letting the info live only
in commit history or scattered plan docs.

## Status table

Snapshot as of 2026-07-11 (updated: Airport DELETE completed).

| Entity | Docs (`01-domain-model.md`) | Scaladoc | Layer consistency | Persistence wiring | HTTP layer | Unit tests | Integration tests (Quill+Doobie) | True endpoint→DB E2E | Migration/schema |
|---|---|---|---|---|---|---|---|---|---|
| **Country** | ✓ full, fresh citations | ✓ | ✓ refactored; minor gap: `CountryCode.from` validator is dead code | ✓ Quill wired | ✓ full CRUD, validated | ✓ both layers | ✓ both adapters | ✓ Postman "E2E — Country" folders | ✓ V1, V7 |
| **Airport** | ✓ full, fresh | ✓ | ✓ refactored; BR-12 pagination-validator gap closed | ✓ Quill wired | ✓ full CRUD, validated (DELETE added, fails on not-found for both Quill+Doobie) | ✓ both layers | ✓ both adapters, incl. delete-not-found case | ✓ Postman "E2E — Airport CRUD lifecycle" folder | ✓ V2/V6/V7/V8 |
| **Airline** | ✓ (rules mostly "unenforced", documented as such) | ✗ none | partial — shares `IcaoCode`, but no domain validation | ✓ Quill wired, find-only | find-only, validated | ✗ no service spec; ✓ endpoint spec | ✗ **none for either adapter** | ✗ none | ✓ V3/V9/V10 (V9 was a late fix) |
| **Route** | ✓ (BR-10 flagged `[MISSING]`) | ✗ none | ✗ none | ✗ **in-memory stub** | create-only, **no GET at all** | ✗ no service spec; ✓ endpoint spec | ✗ **none** (Doobie repo exists, untested) | ✗ none | ✓ V4/V7 |
| **Aircraft** | ✓ (stub-only, noted) | ✗ none | ✗ none | ✗ in-memory stub | find-only, **path param has no Validator** | ✗ no service spec; ✓ endpoint spec | ✗ **no repo exists at all** | ✗ none | ✗ **no table — flagged `[MISSING]`** |
| **Flight** | ✓ (redundant `airlineIcao` flagged, unresolved) | ✗ none | ✗ none | ✗ in-memory stub | find-only, **no Validator** | ✗ no service spec; ✓ endpoint spec | ✗ **no repo exists** | ✗ none | ✗ **no table — `[MISSING]`** |
| **FlightInstance** | ✓ (stub-only) | ✗ none | ✗ none | ✗ in-memory stub | find-only, **has** a UUID `Validator` | ✗ no service spec; ✓ endpoint spec | ✗ **no repo exists** | ✗ none | ✗ **no table — `[MISSING]`** |

Legend: ✓ done · ✗ not done · partial = started but incomplete.

## Summary judgment

- **Fully hardened:** **Country** and **Airport** — both have real persistence, full CRUD,
  scaladoc, both unit-test layers, both Quill+Doobie integration tests, and true HTTP-level
  E2E scenarios. Airport's DELETE endpoint (application service, HTTP endpoint, routing,
  `ApiSpec.allEndpoints` registration, repository not-found semantics for both Quill and
  Doobie, unit tests, integration test, and a Postman E2E lifecycle folder) was completed
  2026-07-11.
- **Partially reviewed:** **Airline** (persisted and documented, but no scaladoc, no write
  path, no integration tests) and **Route** (richly documented gaps, endpoint spec exists,
  but persistence is a stub and it has zero integration tests despite a Doobie repo existing).
- **Not yet reviewed:** **Aircraft, Flight, FlightInstance** — no scaladoc, no persistence
  repos of any kind (Quill or Doobie), no schema/migration, no service-level tests, and (for
  Aircraft/Flight) no HTTP path-param validation. Accurately self-described in `CLAUDE.md` as
  "models + stub endpoints only."

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
