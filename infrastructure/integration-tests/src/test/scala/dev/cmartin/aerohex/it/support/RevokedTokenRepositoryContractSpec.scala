package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.user.RevokedTokenRepository
import java.time.Instant
import java.util.UUID
import zio.ZIO
import zio.test.*

// Behavior contract for QuillRevokedTokenRepositoryItSpec, satisfying the RevokedTokenRepository
// port. Unlike UserRepository, this port has a real revoke method, so no raw-JDBC seeding needed.
object RevokedTokenRepositoryContractSpec:

  def tests: List[Spec[RevokedTokenRepository, Any]] = List(
    test("isRevoked returns false for a jti that was never revoked") {
      for
        repo   <- ZIO.service[RevokedTokenRepository]
        result <- repo.isRevoked(UUID.randomUUID().toString)
      yield assertTrue(!result)
    },
    test("isRevoked returns true after revoke() for that jti") {
      val jti = UUID.randomUUID().toString
      for
        repo   <- ZIO.service[RevokedTokenRepository]
        _      <- repo.revoke(jti, Instant.now().plusSeconds(3600))
        result <- repo.isRevoked(jti)
      yield assertTrue(result)
    },
    test("revoking the same jti twice is not an error") {
      val jti       = UUID.randomUUID().toString
      val expiresAt = Instant.now().plusSeconds(3600)
      for
        repo   <- ZIO.service[RevokedTokenRepository]
        _      <- repo.revoke(jti, expiresAt)
        _      <- repo.revoke(jti, expiresAt)
        result <- repo.isRevoked(jti)
      yield assertTrue(result)
    }
  )
