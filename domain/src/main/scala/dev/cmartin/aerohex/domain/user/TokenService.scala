package dev.cmartin.aerohex.domain.user

import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, UIO}

/** A signed JWT together with its validity window in seconds from the moment of
  * issuance — bundled so callers (e.g. the login HTTP response's `expiresIn`
  * field) never need to re-decode a token just issued to learn its own TTL.
  */
case class AccessToken(value: String, expiresInSeconds: Int)

/** Driven port abstracting JWT issuance/verification — see
  * `plans/security/login.md` decisions 1, 8, and 9 for the claim shape (RFC
  * 7519 registered claims only) and signing algorithm (HS256, symmetric).
  * Implemented by `infrastructure/security`'s `JwtService`.
  */
trait TokenService {

  /** Issues a signed JWT for the given user, embedding `username` as the `sub`
    * claim.
    */
  def generate(username: String): UIO[AccessToken]

  /** Decodes and verifies a token, returning its `sub` claim (the username) on
    * success. Not called from any endpoint yet in this step — see
    * `plans/security/login.md`'s "TokenService.validate belongs in step 1 too"
    * note: its purpose here is to make an expired/invalid token's rejection
    * provable in `JwtServiceSpec`, ahead of an actual HTTP caller in a later
    * step.
    */
  def validate(token: String): IO[DomainError, String]
}
