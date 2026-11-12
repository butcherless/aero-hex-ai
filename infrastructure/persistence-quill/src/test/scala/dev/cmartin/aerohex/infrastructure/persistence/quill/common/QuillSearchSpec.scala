package dev.cmartin.aerohex.infrastructure.persistence.quill.common

import zio.Scope
import zio.test.*

object QuillSearchSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillSearch.containsPattern")(
      test("wraps plain text in % wildcards") {
        assertTrue(QuillSearch.containsPattern("Madrid") == "%Madrid%")
      },
      test("escapes %, _ and backslash so they match literally") {
        assertTrue(QuillSearch.containsPattern("""100%_a\b""") == """%100\%\_a\\b%""")
      }
    )
