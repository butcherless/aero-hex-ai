package dev.cmartin.aerohex.domain.user

/** A user able to authenticate against the API. Aggregate root, identified by
  * its [[username]] — the natural key, and the only field embedded in an issued
  * JWT's `sub` claim (see `TokenService`). No surrogate id here, matching every
  * other entity's convention (see the root `CLAUDE.md`'s `## Database schema`).
  *
  * @param username
  *   the user's unique login identifier and natural key.
  * @param passwordHash
  *   a BCrypt hash of the user's password (see `PasswordHasher`), never the
  *   plaintext password.
  */
case class User(username: String, passwordHash: String)
