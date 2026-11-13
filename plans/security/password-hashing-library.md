# Plan: replace jBCrypt with Password4j

> **Status:** Implemented (2026-11-13).

## Goal

`BcryptPasswordHasher` used `org.mindrot:jbcrypt` 0.4. Its last release was in 2015, and the
upstream repo hasn't changed since 2020. CLAUDE.md's versioning policy asks for stable,
well-maintained direct dependencies. jBCrypt also throws `IllegalArgumentException` on a
malformed stored hash. Inside `ZIO.succeed` that becomes an unhandled crash, not a failed
login. Finally, bcrypt is deliberately slow CPU work, and it ran on the default ZIO thread
pool.

## Decision

**Password4j** (`com.password4j:password4j`, 1.8.4 GA, 2025-06; repo active in 2026; only
runtime dependency `slf4j-api`).

Options checked, in the `choose-library` skill's order:

| Option | Verdict |
|---|---|
| ZIO ecosystem | None. `zio-crypto` was archived in 2024 and never had password hashing. |
| JDK (`PBKDF2WithHmacSHA256`) | Rejected. It is a different algorithm, so every stored `$2a$` hash (including the seeded `admin` user) would need a migration and a rehash-on-login path. That is a larger, separate decision. |
| `at.favre.lib:bcrypt` 0.10.2 | Rejected. Last release 2023-02, so it is only slightly better maintained than jBCrypt. |
| Password4j 1.8.4 | **Chosen.** Maintained. Reads existing `$2a$`/`$2b$`/`$2y$` hashes, so no data migration is needed. Also supports Argon2/scrypt if the algorithm changes later. |
| Spring Security Crypto | Rejected. Pulls in Spring core for one class. |

## Implementation

- `BcryptPasswordHasher.verify` uses `Password.check(plain, hash).with(BcryptFunction.getInstanceFromHash(hash))`.
  This takes cost and version from the stored hash, so every existing hash keeps working.
  It runs in `ZIO.attemptBlocking`; any exception (malformed hash, unsupported version) counts
  as "does not match" and is logged at warn level, instead of crashing the request.
- `simulateVerify` (the user-enumeration timing guard from `login.md`'s 2026-11-13 revision)
  checks against a dummy hash made by the same library at the same cost, 10.
- The port (`PasswordHasher`) is unchanged.

## Files touched

`project/Versions.scala`, `project/Dependencies.scala`, `build.sbt` (security module),
`BcryptPasswordHasher.scala`, `BcryptPasswordHasherSpec.scala`.
