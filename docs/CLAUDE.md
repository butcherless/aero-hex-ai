# Docs directory

`docs/` holds analysis and API artifacts (distinct from `plans/`, which holds implementation designs):

- `docs/analysis/01-domain-model.md` — DDD glossary + domain model with standard IATA/ICAO
  terminology; the source of truth for entity/value-object definitions.
- `docs/analysis/entity-relationship-draft.md` — working notes on entity relationships and
  cardinalities; a scratch space, **not** a source of truth — conclusions get promoted into
  `01-domain-model.md`.
- `docs/analysis/validation-analysis-hexagonal.md` — the validation-design rationale behind
  the root `CLAUDE.md`'s `## Key patterns` opaque-types convention (why smart constructors, why
  accumulate-vs-fail-fast).
- `docs/analysis-plan.md` — the task plan that drives the analysis docs (glossary → use cases → ADR).
- `docs/api/collection.json` + `environment.json` — Postman collection kept in sync with the
  Tapir-generated OpenAPI spec via the `sync-postman-collection` skill; regenerate after any
  endpoint change, never edit by hand. Its 5 `E2E — ...` folders are runnable against a live app
  via the `run-e2e-tests` skill — see the root `CLAUDE.md`'s `## Validation` section.
- `docs/api/endpoint-status.md` — per-endpoint implementation status table (see the root
  `CLAUDE.md`'s `## REST API` section); update whenever an endpoint's status changes.
- `docs/todo/` — analysis for future work. `auth-jwt.md` (JWT auth with Tapir + ZIO) is still
  idea-stage; `master-data/analysis.md` is further along — architecture decided and fully
  implemented (Country, Airport, and Airline sync all end-to-end against real Postgres), with its
  own `plans/masterdata/` subdirectory of implementation-increment docs.
