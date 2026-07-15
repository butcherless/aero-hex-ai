package dev.cmartin.aerohex.domain.country

import zio.test.*

object CountryCodeSpec extends ZIOSpecDefault:

  private def errorsOf(raw: String): List[String] =
    CountryCode.validateAll(raw).toEither.fold(_.toChunk.toList, _ => Nil)

  override def spec: Spec[TestEnvironment, Any] =
    suite("CountryCode.validateAll")(
      test("succeeds for a valid 2-letter code") {
        assertTrue(CountryCode.validateAll("ES").toEither.isRight)
      },
      test("fails with exactly one error when only the length rule is violated") {
        assertTrue(errorsOf("ESP") == List("country code must be exactly 2 characters"))
      },
      test("fails with exactly one error when only the letters-only rule is violated") {
        assertTrue(errorsOf("1A") == List("country code must contain only letters"))
      },
      test("accumulates all three errors for a fully empty code") {
        assertTrue(
          errorsOf("") == List(
            "country code must not be empty",
            "country code must be exactly 2 characters",
            "country code must contain only letters"
          )
        )
      }
    )
