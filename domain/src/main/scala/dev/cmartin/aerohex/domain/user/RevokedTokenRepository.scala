package dev.cmartin.aerohex.domain.user

import java.time.Instant
import zio.UIO

trait RevokedTokenRepository {
  def isRevoked(jti: String): UIO[Boolean]
  def revoke(jti: String, expiresAt: Instant): UIO[Unit]
}
