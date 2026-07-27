package dev.cmartin.aerohex.domain.user

import zio.UIO

/** Driven port abstracting password hashing/verification. Implemented by
  * `infrastructure/security`'s `BcryptPasswordHasher`.
  */
trait PasswordHasher {

  /** Compares a plaintext password against a previously-hashed one. */
  def verify(plain: String, hash: String): UIO[Boolean]
}
