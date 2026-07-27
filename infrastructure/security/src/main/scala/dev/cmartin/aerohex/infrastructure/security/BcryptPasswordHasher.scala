package dev.cmartin.aerohex.infrastructure.security

import dev.cmartin.aerohex.domain.user.PasswordHasher
import org.mindrot.jbcrypt.BCrypt
import zio.{UIO, ULayer, ZIO, ZLayer}

final class BcryptPasswordHasher extends PasswordHasher {
  override def verify(plain: String, hash: String): UIO[Boolean] =
    ZIO.succeed(BCrypt.checkpw(plain, hash))
}

object BcryptPasswordHasher {
  val layer: ULayer[PasswordHasher] = ZLayer.succeed(new BcryptPasswordHasher)
}
