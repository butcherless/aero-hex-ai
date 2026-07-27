package dev.cmartin.aerohex.domain.user

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait LoginUseCase {
  def login(username: String, password: String): IO[DomainError, AccessToken]
}
