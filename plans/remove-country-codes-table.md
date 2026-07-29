# Plan: Move ISO country-code membership check from `country_codes` table into `Country` domain code

## Goal

Retire the standalone `country_codes` Postgres table (`V12__create_country_codes.sql`) and its
`CountryRepository.validateCode` port method, replacing the "is this a real ISO 3166-1 alpha-2
code" check (BR-16) with a pure, in-memory check in the `domain` module.

## Decision

**Move the check into `CountryCode` as a pure function backed by a hardcoded `Set[String]`,
instead of a database lookup.**

The ISO 3166-1 alpha-2 list is external, static reference data — it changes on the order of once
every few years (a country gaining/losing recognition), never at application runtime. A database
round trip for a 249-entry membership test bought correctness `domain` can get for free from a
plain `Set`, at the cost of a Postgres dependency for what is otherwise a pure function, and made
the check untestable without Testcontainers.

This does **not** contradict `docs/analysis/validation-analysis-hexagonal.md` §3's warning against
"hardcoding a second, driftable copy of ISO 3166 inside the domain module" — that warning was about
a different check, BR-04 (does a referenced parent already exist in *this app's own*, dynamic
`countries` table), where a hardcoded copy really would drift from live data. BR-16 is membership in
an external standard, not a lookup against this app's own mutable state.

**Checked first, no prior art to revert to:** `git log --all -p -- '**/Country*.scala'` and the
commit that introduced this feature (`d058187`, "Validate Country codes against a real ISO
3166-1 alpha-2 reference table") show the DB-backed table was the *only* implementation this check
ever had — never a pure in-domain version that got deprecated in favor of the table. This is a new
direction, not a rollback.

**Rejected alternative:** leave `country_codes` in place and only add the pure check alongside it
(belt-and-braces). Rejected — the DB table has no other consumer and no FK, so keeping both is pure
duplication with no safety benefit; the domain check is the source of truth either way.

## Scope, explicitly bounded

Structural move only, not a behavior fix. `CountryCode`'s shape assertion accepts lowercase
(`^[a-zA-Z]{2}$`), while the ISO reference data is uppercase-only, so a valid-but-lowercase input
like `"es"` is (and remains) rejected as "not recognized" with a misleading message. The new
in-memory check preserves the old `EXISTS` query's case-sensitive exact match exactly — this is a
separate, previously-flagged bug, tracked independently, not addressed here.

## Steps

1. **Domain**: add `domain/country/IsoCountryCodes.scala` (`private[country]`, a `Set[String]` of
   all 249 codes, mirrored from V12's `INSERT` list) and `CountryCode.isRecognized`/`validateIso` in
   `domain/country/Country.scala`.
2. **Domain port**: remove `validateCode` from `CountryRepository` — no longer I/O, so it doesn't
   belong on a port.
3. **Application**: `CreateCountryService.create` calls `ZIO.fromEither(CountryCode.validateIso(...))`
   instead of `repo.validateCode(...)`; same sequencing (membership check before `AlreadyExists`),
   same error type/message.
4. **Persistence**: remove `validateCode`'s `EXISTS` query and Quill "questionable row class"
   workaround comment from `QuillCountryRepository`.
5. **Schema**: add `V19__drop_country_codes.sql` (`DROP TABLE IF EXISTS country_codes`). `V12` stays
   untouched — Flyway migrations are immutable once applied; editing/removing an already-applied
   migration breaks checksum validation on any environment that already ran it.
6. **Tests**: drop `validateCode` from `CountryRepositoryStub`; update `CountryServiceSpec`'s three
   `CreateCountryService` tests (the "invalid code" case now exercises the real `validateIso` check
   via `CountryCode("ZZ")` instead of a stubbed failure); add `isRecognized`/`validateIso` unit tests
   to `CountryCodeSpec`; delete the two `validateCode` tests from
   `CountryRepositoryContractSpec` (integration-tests module).
7. **Docs**: update root `CLAUDE.md`, `docs/analysis/01-domain-model.md` (BR-16 + §6 constraints
   table), `docs/analysis/validation-analysis-hexagonal.md` (§2.4a marked `[SUPERSEDED]`),
   `plans/entity-review-progress.md`, `infrastructure/integration-tests/CLAUDE.md`.

## Files touched

- `domain/src/main/scala/dev/cmartin/aerohex/domain/country/IsoCountryCodes.scala` (new)
- `domain/src/main/scala/dev/cmartin/aerohex/domain/country/Country.scala`
- `domain/src/main/scala/dev/cmartin/aerohex/domain/country/CountryRepository.scala`
- `application/src/main/scala/dev/cmartin/aerohex/application/country/CreateCountryService.scala`
- `infrastructure/persistence-quill/src/main/scala/.../country/QuillCountryRepository.scala`
- `infrastructure/migration/src/main/resources/db/migration/V19__drop_country_codes.sql` (new)
- `application/src/test/scala/dev/cmartin/aerohex/application/country/CountryRepositoryStub.scala`
- `application/src/test/scala/dev/cmartin/aerohex/application/country/CountryServiceSpec.scala`
- `domain/src/test/scala/dev/cmartin/aerohex/domain/country/CountryCodeSpec.scala`
- `infrastructure/integration-tests/src/test/scala/.../support/CountryRepositoryContractSpec.scala`
- `CLAUDE.md`, `docs/analysis/01-domain-model.md`, `docs/analysis/validation-analysis-hexagonal.md`,
  `plans/entity-review-progress.md`, `infrastructure/integration-tests/CLAUDE.md`

## Verification

1. `sbt scalafmtAll` then `sbt compile` — zero errors/warnings.
2. `sbt test` — domain, application, adapter-http suites all green.
3. `sbt integrationTests/test` — `FlywayMigrationItSpec` reaches V19; `QuillCountryRepositoryItSpec`
   passes without the removed `validateCode` cases.
4. Smoke test against the running app: `POST /api/v1/countries` with a fake code (`"ZZ"`) still
   400s with `InvalidCountryCode`; a real unused code still succeeds — same external behavior,
   no DB round trip for this check anymore.
