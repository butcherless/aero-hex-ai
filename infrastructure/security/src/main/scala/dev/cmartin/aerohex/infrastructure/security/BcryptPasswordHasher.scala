package dev.cmartin.aerohex.infrastructure.security

import dev.cmartin.aerohex.domain.user.PasswordHasher
import java.util.UUID
import org.mindrot.jbcrypt.BCrypt
import zio.{UIO, ULayer, ZIO, ZLayer}

/** @param dummyCost
  *   bcrypt cost factor of the hash `simulateVerify` checks against. It should
  *   match the cost of the stored user hashes, or the timing still differs.
  *   Default 10 is `BCrypt.gensalt()`'s own default.
  */
final class BcryptPasswordHasher(dummyCost: Int = 10) extends PasswordHasher {

  // Hash of a random value nobody knows, so checking any password against it always fails.
  private val dummyHash: String = BCrypt.hashpw(UUID.randomUUID().toString, BCrypt.gensalt(dummyCost))

  override def verify(plain: String, hash: String): UIO[Boolean] =
    ZIO.succeed(BCrypt.checkpw(plain, hash))

  override def simulateVerify(plain: String): UIO[Unit] =
    verify(plain, dummyHash).unit
}

object BcryptPasswordHasher {
  val layer: ULayer[PasswordHasher] = ZLayer.succeed(new BcryptPasswordHasher())
}
