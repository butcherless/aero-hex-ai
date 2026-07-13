package dev.cmartin.aerohex.shared

import zio.test.*

object NonEmptyStringSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment, Any] =
    suite("NonEmptyString")(
      test("from succeeds and trims a non-blank value") {
        assertTrue(NonEmptyString.from("  Madrid  ").map(_.value) == Right("Madrid"))
      },
      test("from fails for a blank value") {
        assertTrue(NonEmptyString.from("   ") == Left("Value must not be blank"))
      },
      test("unsafeFrom returns the value for a non-blank string") {
        assertTrue(NonEmptyString.unsafeFrom("Madrid").value == "Madrid")
      },
      test("unsafeFrom throws for a blank string") {
        assertTrue(
          scala.util.Try(NonEmptyString.unsafeFrom("   ")).isFailure
        )
      }
    )
