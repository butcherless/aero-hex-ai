package dev.cmartin.aerohex.infrastructure.persistence.quill.user

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{User, UserRepository}
import io.getquill.*
import io.getquill.jdbczio.Quill
import javax.sql.DataSource
import zio.{IO, URLayer, ZLayer}

final class QuillUserRepository(dataSource: DataSource) extends UserRepository {

  // Carries the DB's surrogate id for consistency with every other table's row shape, even
  // though nothing FKs to `users` yet; dropped in toUser, matching QuillCountryRepository
  // mapping CountryRow(id, code, name) down to Country(code, name).
  private case class UserRow(id: Long, username: String, passwordHash: String)

  private val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toUser(row: UserRow): User = User(row.username, row.passwordHash)

  override def findByUsername(username: String): IO[DomainError, Option[User]] =
    ctx
      .run(quote {
        querySchema[UserRow]("users").filter(_.username == lift(username))
      })
      .map(_.headOption.map(toUser))
      .orDie
}

object QuillUserRepository {
  val layer: URLayer[DataSource, UserRepository] =
    ZLayer.fromFunction(new QuillUserRepository(_))
}
