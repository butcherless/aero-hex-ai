package dev.cmartin.aerohex.domain.user

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait UserRepository {
  def findByUsername(username: String): IO[DomainError, Option[User]]
}
