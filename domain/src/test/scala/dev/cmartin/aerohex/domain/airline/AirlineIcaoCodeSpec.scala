package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.support.ValidationTestSupport.accumulatedErrors
import zio.test.*

object AirlineIcaoCodeSpec extends ZIOSpecDefault:

  private val errorsOf = accumulatedErrors(AirlineIcaoCode.validateAll)

  override def spec: Spec[TestEnvironment, Any] =
    suite("AirlineIcaoCode.validateAll")(
      test("succeeds for a valid 3-letter code") {
        assertTrue(AirlineIcaoCode.validateAll("IBE").toEither.isRight)
      },
      test("fails with exactly one error when only the length rule is violated") {
        assertTrue(errorsOf("IBER") == List("airline ICAO code must be exactly 3 characters"))
      },
      test("fails with exactly one error when only the letters-only rule is violated") {
        assertTrue(errorsOf("1BE") == List("airline ICAO code must contain only letters"))
      },
      test("accumulates all three errors for a fully empty code") {
        assertTrue(
          errorsOf("") == List(
            "airline ICAO code must not be empty",
            "airline ICAO code must be exactly 3 characters",
            "airline ICAO code must contain only letters"
          )
        )
      }
    )
