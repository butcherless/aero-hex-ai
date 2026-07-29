package dev.cmartin.aerohex.domain.country

import dev.cmartin.aerohex.domain.error.DomainError
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
    ) +
      suite("CountryCode.isRecognized / validateIso")(
        test("isRecognized is true for a real ISO 3166-1 alpha-2 code") {
          assertTrue(CountryCode.isRecognized(CountryCode("ES")))
        },
        test("isRecognized is false for a syntactically-valid but fake code") {
          assertTrue(!CountryCode.isRecognized(CountryCode("ZZ")))
        },
        test("validateIso succeeds for a real ISO code") {
          assertTrue(CountryCode.validateIso(CountryCode("JP")) == Right(()))
        },
        test("validateIso fails with InvalidCountryCode for a fake code") {
          assertTrue(
            CountryCode.validateIso(CountryCode("ZZ")) ==
              Left(DomainError.InvalidCountryCode(List("ZZ is not a recognized ISO 3166-1 alpha-2 country code")))
          )
        }
      )
