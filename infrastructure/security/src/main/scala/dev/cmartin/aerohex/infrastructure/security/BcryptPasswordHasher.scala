package dev.cmartin.aerohex.infrastructure.security

import com.password4j.types.Bcrypt
import com.password4j.{BcryptFunction, Password}
import dev.cmartin.aerohex.domain.user.PasswordHasher
import java.util.UUID
import zio.{UIO, ULayer, ZIO, ZLayer}

/** bcrypt via Password4j (see `plans/security/password-hashing-library.md`).
  *
  * @param dummyCost
  *   bcrypt cost factor of the hash `simulateVerify` checks against. It should
  *   match the cost of the stored user hashes, or the timing still differs.
  *   Default 10 matches the seeded users (`$2a$10$...`).
  */
final class BcryptPasswordHasher(dummyCost: Int = 10) extends PasswordHasher {

  // Hash of a random value nobody knows, so checking any password against it always fails.
  private val dummyHash: String =
    BcryptFunction.getInstance(Bcrypt.A, dummyCost).hash(UUID.randomUUID().toString).getResult

  // Cost and version come from the stored hash itself, so existing $2a$/$2b$/$2y$ hashes all
  // verify unchanged. bcrypt is slow on purpose, so it runs on the blocking pool. A malformed
  // stored hash counts as "no match" and is logged, instead of crashing the request.
  override def verify(plain: String, hash: String): UIO[Boolean] =
    ZIO
      .attemptBlocking(Password.check(plain, hash).`with`(BcryptFunction.getInstanceFromHash(hash)))
      .catchAll(e => ZIO.logWarning(s"Password check failed on a stored hash: ${e.getMessage}").as(false))

  override def simulateVerify(plain: String): UIO[Unit] =
    verify(plain, dummyHash).unit
}

object BcryptPasswordHasher {
  val layer: ULayer[PasswordHasher] = ZLayer.succeed(new BcryptPasswordHasher())
}
