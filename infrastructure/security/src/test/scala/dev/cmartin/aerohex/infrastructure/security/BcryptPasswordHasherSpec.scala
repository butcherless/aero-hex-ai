package dev.cmartin.aerohex.infrastructure.security

import com.password4j.BcryptFunction
import com.password4j.types.Bcrypt
import zio.Scope
import zio.test.*

object BcryptPasswordHasherSpec extends ZIOSpecDefault:

  // Low cost factor (4) purely to keep the test fast. verify reads the cost from the hash, so any
  // cost works.
  private val hash = BcryptFunction.getInstance(Bcrypt.B, 4).hash("s3cret!").getResult

  // The seeded dev user's real hash format ($2a$, cost 10, made by jBCrypt): existing hashes must
  // keep verifying after the library swap. Plaintext from plans/seed-data-users.sql.
  private val seededAdminHash = "$2a$10$EIX.wpA4zhI8M4woo0rJf.6qNWUcdm7SIv2bcgpxW5BZrzw8cCEy."

  private val hasher = new BcryptPasswordHasher(dummyCost = 4)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("BcryptPasswordHasher")(
      test("verify succeeds for the correct plaintext against its own hash") {
        for result <- hasher.verify("s3cret!", hash)
        yield assertTrue(result)
      },
      test("verify fails for the wrong plaintext") {
        for result <- hasher.verify("wrong-password", hash)
        yield assertTrue(!result)
      },
      test("verify accepts an existing jBCrypt-generated $2a$ hash") {
        for
          right <- hasher.verify("ChangeMe123!", seededAdminHash)
          wrong <- hasher.verify("not-it", seededAdminHash)
        yield assertTrue(right, !wrong)
      },
      test("verify returns false, instead of dying, for a malformed stored hash") {
        for result <- hasher.verify("anything", "not-a-bcrypt-hash")
        yield assertTrue(!result)
      },
      test("simulateVerify completes without needing a stored hash") {
        for exit <- hasher.simulateVerify("anything").exit
        yield assertTrue(exit.isSuccess)
      }
    )
