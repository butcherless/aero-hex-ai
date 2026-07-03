package dev.cmartin.aerohex.application.aspect

import zio.{Scope, ZIO}
import zio.test.*

object ServiceAspectSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ServiceAspect")(
      test("logs around a successful effect and returns its result unchanged") {
        for result <- ZIO.succeed(42) @@ ServiceAspect.logged("test-success")
        yield assertTrue(result == 42)
      },
      test("logs around a failing effect and propagates the error unchanged") {
        for error <- (ZIO.fail("boom") @@ ServiceAspect.logged("test-failure")).flip
        yield assertTrue(error == "boom")
      }
    )
