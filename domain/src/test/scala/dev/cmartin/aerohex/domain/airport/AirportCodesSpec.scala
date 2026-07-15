package dev.cmartin.aerohex.domain.airport

import zio.test.*

object AirportCodesSpec extends ZIOSpecDefault:

  private def iataErrorsOf(raw: String): List[String] =
    IataCode.validateAll(raw).toEither.fold(_.toChunk.toList, _ => Nil)

  private def icaoErrorsOf(raw: String): List[String] =
    AirportIcaoCode.validateAll(raw).toEither.fold(_.toChunk.toList, _ => Nil)

  override def spec: Spec[TestEnvironment, Any] =
    suite("Airport codes")(
      suite("IataCode.validateAll")(
        test("succeeds for a valid 3-letter code") {
          assertTrue(IataCode.validateAll("MAD").toEither.isRight)
        },
        test("fails with exactly one error when only the length rule is violated") {
          assertTrue(iataErrorsOf("MADX") == List("IATA code must be exactly 3 characters"))
        },
        test("fails with exactly one error when only the letters-only rule is violated") {
          assertTrue(iataErrorsOf("M4D") == List("IATA code must contain only letters"))
        },
        test("accumulates all three errors for a fully empty code") {
          assertTrue(
            iataErrorsOf("") == List(
              "IATA code must not be empty",
              "IATA code must be exactly 3 characters",
              "IATA code must contain only letters"
            )
          )
        }
      ),
      suite("AirportIcaoCode.validateAll")(
        test("succeeds for a valid 4-letter code") {
          assertTrue(AirportIcaoCode.validateAll("LEMD").toEither.isRight)
        },
        test("fails with exactly one error when only the length rule is violated") {
          assertTrue(icaoErrorsOf("LEM") == List("airport ICAO code must be exactly 4 characters"))
        },
        test("fails with exactly one error when only the letters-only rule is violated") {
          assertTrue(icaoErrorsOf("LE1D") == List("airport ICAO code must contain only letters"))
        },
        test("accumulates all three errors for a fully empty code") {
          assertTrue(
            icaoErrorsOf("") == List(
              "airport ICAO code must not be empty",
              "airport ICAO code must be exactly 4 characters",
              "airport ICAO code must contain only letters"
            )
          )
        }
      )
    )
