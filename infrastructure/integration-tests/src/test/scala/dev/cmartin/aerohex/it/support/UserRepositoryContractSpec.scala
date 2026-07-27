package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.user.{User, UserRepository}
import javax.sql.DataSource
import zio.ZIO
import zio.test.*

// Behavior contract for QuillUserRepositoryItSpec, satisfying the UserRepository port.
// UserRepository intentionally has no save/create method yet (no registration endpoint in this
// step — plans/security/login.md decision 5), so seeding a row for the "found" case goes
// straight through the DataSource via a raw JDBC insert, bypassing the port entirely (test-only
// plumbing; no production code involved).
object UserRepositoryContractSpec:

  private def seedUser(username: String, passwordHash: String): ZIO[DataSource, Throwable, Unit] =
    ZIO.serviceWithZIO[DataSource] { ds =>
      ZIO.attemptBlocking {
        val conn = ds.getConnection
        try {
          val stmt = conn.prepareStatement("INSERT INTO users (username, password_hash) VALUES (?, ?)")
          try {
            stmt.setString(1, username)
            stmt.setString(2, passwordHash)
            stmt.executeUpdate()
          } finally stmt.close()
        } finally conn.close()
      }
    }

  // Exactly 60 characters, bcrypt-hash-shaped ($2a$10$ prefix + 53 more) — password_hash is
  // CHAR(60), and a value shorter than that gets space-padded by Postgres on read, which a real
  // bcrypt hash (always exactly 60 chars) never triggers in production.
  private val fakeHash = "$2a$10$" + ("a" * 53)

  def tests: List[Spec[UserRepository & DataSource, Any]] = List(
    test("findByUsername returns the seeded user") {
      for
        _      <- seedUser("alice", fakeHash)
        repo   <- ZIO.service[UserRepository]
        result <- repo.findByUsername("alice")
      yield assertTrue(result.contains(User("alice", fakeHash)))
    },
    test("findByUsername returns None for an unknown username") {
      for
        repo   <- ZIO.service[UserRepository]
        result <- repo.findByUsername("does-not-exist-at-all")
      yield assertTrue(result.isEmpty)
    }
  )
