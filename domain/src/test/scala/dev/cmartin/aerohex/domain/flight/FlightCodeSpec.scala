package dev.cmartin.aerohex.domain.flight

import zio.test.*

object FlightCodeSpec extends ZIOSpecDefault:

  private def errorsOf(raw: String): List[String] =
    FlightCode.validateAll(raw).toEither.fold(_.toChunk.toList, _ => Nil)

  override def spec: Spec[TestEnvironment, Any] =
    suite("FlightCode.validateAll")(
      test("succeeds for a valid flight code") {
        assertTrue(FlightCode.validateAll("UX9117").toEither.isRight)
      },
      test("fails with exactly one error when blank") {
        // notBlank can never fail together with maxLength/singleLine here (an empty string is
        // always within the 8-char bound and never contains a newline).
        assertTrue(errorsOf("") == List("flight code must not be empty"))
      },
      test("fails with exactly one error when longer than 8 characters") {
        assertTrue(errorsOf("AEA91178X") == List("flight code must be at most 8 characters"))
      },
      test("fails with exactly one error when it contains a newline") {
        assertTrue(errorsOf("UX\n9117") == List("flight code must not contain a newline"))
      },
      test("accumulates both errors when longer than 8 characters and containing a newline") {
        assertTrue(
          errorsOf("AEA9117\n8X") == List(
            "flight code must be at most 8 characters",
            "flight code must not contain a newline"
          )
        )
      }
    )
