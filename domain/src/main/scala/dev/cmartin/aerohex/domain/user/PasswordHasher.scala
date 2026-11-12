package dev.cmartin.aerohex.domain.user

import zio.UIO

/** Driven port abstracting password hashing/verification. Implemented by
  * `infrastructure/security`'s `BcryptPasswordHasher`.
  */
trait PasswordHasher {

  /** Compares a plaintext password against a previously-hashed one. */
  def verify(plain: String, hash: String): UIO[Boolean]

  /** Does the same work as a failing `verify`, but there is no stored hash to
    * check against. Login calls it for an unknown username, so that case takes
    * as long as a wrong password. Otherwise response time would reveal which
    * usernames exist.
    */
  def simulateVerify(plain: String): UIO[Unit]
}
