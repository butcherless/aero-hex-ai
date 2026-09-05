package dev.cmartin.aerohex.application.support

import zio.IO
import zio.test.*

object ServiceLayerSpecSupport:

  // Every application service's spec repeats a "layer wires up" smoke test that differs only in
  // the service name and the ZIO.service[...].provide(...) expression the caller already built.
  def constructsUsableInstance[E](name: String)(effect: IO[E, Any]): Spec[Any, Any] =
    test(s"$name.layer constructs a usable instance") {
      for _ <- effect
      yield assertCompletes
    }
