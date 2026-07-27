package dev.cmartin.aerohex.infrastructure.persistence.quill.user

import dev.cmartin.aerohex.domain.user.RevokedTokenRepository
import io.getquill.*
import io.getquill.jdbczio.Quill
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import zio.{UIO, URLayer, ZLayer}

final class QuillRevokedTokenRepository(dataSource: DataSource) extends RevokedTokenRepository {

  private case class RevokedTokenRow(jti: UUID, expiresAt: Instant)

  private val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  override def isRevoked(jti: String): UIO[Boolean] =
    ctx
      .run(quote {
        querySchema[RevokedTokenRow]("revoked_tokens").filter(_.jti == lift(UUID.fromString(jti)))
      })
      .map(_.nonEmpty)
      .orDie

  // ON CONFLICT DO NOTHING at the SQL level, not a caught unique-violation, since a duplicate
  // revoke of the same jti is a genuine no-op (see plans/security/logout.md decision 1) — not an
  // error case worth threading a DomainError for, unlike every other insert in this codebase.
  override def revoke(jti: String, expiresAt: Instant): UIO[Unit] =
    ctx
      .run(quote {
        querySchema[RevokedTokenRow]("revoked_tokens")
          .insert(_.jti -> lift(UUID.fromString(jti)), _.expiresAt -> lift(expiresAt))
          .onConflictIgnore
      })
      .unit
      .orDie
}

object QuillRevokedTokenRepository {
  val layer: URLayer[DataSource, RevokedTokenRepository] =
    ZLayer.fromFunction(new QuillRevokedTokenRepository(_))
}
