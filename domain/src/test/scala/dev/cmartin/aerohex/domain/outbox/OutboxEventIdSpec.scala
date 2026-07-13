package dev.cmartin.aerohex.domain.outbox

import java.util.UUID
import zio.test.*

object OutboxEventIdSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment, Any] =
    suite("OutboxEventId")(
      test("apply wraps a UUID and value unwraps it back") {
        val uuid = UUID.fromString("b1c2d3e4-f5a6-7890-bcde-f01234567890")
        assertTrue(OutboxEventId(uuid).value == uuid)
      },
      test("generate produces a different id on each call") {
        assertTrue(OutboxEventId.generate != OutboxEventId.generate)
      }
    )
