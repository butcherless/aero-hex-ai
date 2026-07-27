package dev.cmartin.aerohex.domain.user

import dev.cmartin.aerohex.domain.error.DomainError
import java.time.Instant
import zio.{IO, UIO}

/** A signed JWT together with its validity window in seconds from the moment of
  * issuance — bundled so callers (e.g. the login HTTP response's `expiresIn`
  * field) never need to re-decode a token just issued to learn its own TTL.
  */
case class AccessToken(value: String, expiresInSeconds: Int)

/** The resolved principal of a successfully validated token — `jti`/`expiresAt`
  * exist specifically so `logout` (`plans/security/logout.md`) can revoke
  * *this* token, not just identify the caller. Every protected endpoint except
  * `logout` discards this value (`.serverLogic { _ => args => ... }`); only
  * `AuthRoutes` actually uses `jti`/`expiresAt`.
  */
case class ValidatedToken(username: String, jti: String, expiresAt: Instant)

/** Driven port abstracting JWT issuance/verification/revocation — see
  * `plans/security/login.md` decisions 1, 8, and 9 for the claim shape (RFC
  * 7519 registered claims only) and signing algorithm (HS256, symmetric), and
  * `plans/security/logout.md` for revocation. Implemented by
  * `infrastructure/security`'s `JwtService`.
  */
trait TokenService {

  /** Issues a signed JWT for the given user, embedding `username` as the `sub`
    * claim.
    */
  def generate(username: String): UIO[AccessToken]

  /** Decodes and verifies a token — signature, `exp`/`nbf`/`iss`/`aud`, and
    * (since step 3) whether it's been revoked — returning its resolved
    * principal on success.
    */
  def validate(token: String): IO[DomainError, ValidatedToken]

  /** Revokes a token by its `jti`, so a subsequent `validate` call for it fails
    * with `DomainError.TokenRevoked` even though it hasn't naturally expired
    * yet. Idempotent — revoking an already-revoked `jti` is not an error.
    */
  def revoke(jti: String, expiresAt: Instant): UIO[Unit]
}
