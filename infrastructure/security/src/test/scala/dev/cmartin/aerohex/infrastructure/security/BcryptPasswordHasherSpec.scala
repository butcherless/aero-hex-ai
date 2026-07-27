package dev.cmartin.aerohex.infrastructure.security

import org.mindrot.jbcrypt.BCrypt
import zio.Scope
import zio.test.*

object BcryptPasswordHasherSpec extends ZIOSpecDefault:

  // Low cost factor (4) purely to keep the test fast — production hashing cost is unrelated
  // (BcryptPasswordHasher.verify just delegates to BCrypt.checkpw, which works at any cost).
  private val hash = BCrypt.hashpw("s3cret!", BCrypt.gensalt(4))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("BcryptPasswordHasher")(
      test("verify succeeds for the correct plaintext against its own hash") {
        for result <- new BcryptPasswordHasher().verify("s3cret!", hash)
        yield assertTrue(result)
      },
      test("verify fails for the wrong plaintext") {
        for result <- new BcryptPasswordHasher().verify("wrong-password", hash)
        yield assertTrue(!result)
      }
    )
