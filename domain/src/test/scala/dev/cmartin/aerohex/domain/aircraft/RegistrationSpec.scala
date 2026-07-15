package dev.cmartin.aerohex.domain.aircraft

import zio.test.*

object RegistrationSpec extends ZIOSpecDefault:

  private def errorsOf(raw: String): List[String] =
    Registration.validateAll(raw).toEither.fold(_.toChunk.toList, _ => Nil)

  override def spec: Spec[TestEnvironment, Any] =
    suite("Registration.validateAll")(
      test("succeeds for a valid registration") {
        assertTrue(Registration.validateAll("EC-MIG").toEither.isRight)
      },
      test("fails with exactly one error when blank") {
        // notBlank can never fail together with maxLength/singleLine here (an empty string is
        // always within the 10-char bound and never contains a newline).
        assertTrue(errorsOf("") == List("registration must not be empty"))
      },
      test("fails with exactly one error when longer than 10 characters") {
        assertTrue(errorsOf("EC-MIGMIGXX") == List("registration must be at most 10 characters"))
      },
      test("fails with exactly one error when it contains a newline") {
        assertTrue(errorsOf("EC-MIG\nX") == List("registration must not contain a newline"))
      },
      test("accumulates both errors when longer than 10 characters and containing a newline") {
        assertTrue(
          errorsOf("EC-MIGMIG\nXX") == List(
            "registration must be at most 10 characters",
            "registration must not contain a newline"
          )
        )
      }
    )
