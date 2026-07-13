package dev.cmartin.aerohex.domain.flight

import java.util.UUID
import zio.test.*

object FlightInstanceIdSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment, Any] =
    suite("FlightInstanceId")(
      test("apply wraps a UUID and value unwraps it back") {
        val uuid = UUID.fromString("b1c2d3e4-f5a6-7890-bcde-f01234567890")
        assertTrue(FlightInstanceId(uuid).value == uuid)
      },
      test("generate produces a different id on each call") {
        assertTrue(FlightInstanceId.generate != FlightInstanceId.generate)
      }
    )
