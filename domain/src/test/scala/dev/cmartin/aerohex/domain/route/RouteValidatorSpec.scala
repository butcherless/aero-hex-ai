package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError.InvalidRoute
import zio.test.*

object RouteValidatorSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment, Any] =
    suite("RouteValidator")(
      test("succeeds when origin and destination differ and distance is positive") {
        for result <- RouteValidator.validate(IataCode("MAD"), IataCode("BCN"), 483).exit
        yield assertTrue(result.isSuccess)
      },
      test("fails with InvalidRoute when origin and destination are the same airport") {
        for error <- RouteValidator.validate(IataCode("MAD"), IataCode("MAD"), 100).flip
        yield assertTrue(error == InvalidRoute("Origin and destination cannot be the same airport"))
      },
      test("fails with InvalidRoute when the distance is zero") {
        for error <- RouteValidator.validate(IataCode("MAD"), IataCode("BCN"), 0).flip
        yield assertTrue(error == InvalidRoute("Distance must be positive, got 0"))
      },
      test("fails with InvalidRoute when the distance is negative") {
        for error <- RouteValidator.validate(IataCode("MAD"), IataCode("BCN"), -10).flip
        yield assertTrue(error == InvalidRoute("Distance must be positive, got -10"))
      }
    )
