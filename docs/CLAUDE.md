# Docs directory

`docs/` holds analysis and API artifacts (`plans/` holds implementation designs):

- `analysis/01-domain-model.md`: DDD glossary and domain model; the source of truth for entity and value-object definitions.
- `analysis/entity-relationship-draft.md`: scratch notes, **not** a source of truth. Conclusions get promoted to `01-domain-model.md`.
- `analysis/validation-analysis-hexagonal.md`: why smart constructors, and why accumulate vs. fail-fast.
- `analysis-plan.md`: the task plan behind the analysis docs.
- `aircraft-fleet-demo-data.md`: manual recipe for loading demo aircraft from OpenSky.
- `api/collection.json` + `environment.json`: Postman collection. Regenerate with the `sync-postman-collection` skill after any endpoint change; never edit by hand. Its `E2E — ...` folders run through the `run-e2e-tests` skill.
- `api/endpoint-status.md`: per-endpoint status; update it whenever a status changes.
- `todo/auth-jwt.md`: JWT auth, with the security rollout's `## Roadmap`. Per-step designs are in `plans/security/`.
- `todo/security/security-analysis-aero-hex-ai.md`: an earlier pass, kept as historical rationale.
- `todo/master-data/analysis.md`: master-data sync analysis. Increment docs are in `plans/masterdata/`.
