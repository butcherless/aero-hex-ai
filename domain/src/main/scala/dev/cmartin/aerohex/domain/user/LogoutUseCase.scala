package dev.cmartin.aerohex.domain.user

import java.time.Instant
import zio.UIO

trait LogoutUseCase {
  def logout(jti: String, expiresAt: Instant): UIO[Unit]
}
