package dev.cmartin.aerohex.application.aspect

import zio.{Trace, ZIO, ZIOAspect}

object ServiceAspect:

  def logged(label: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any]:
      override def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.logDebug(s"$label - started") *>
          zio
            .tap(_ => ZIO.logDebug(s"$label - completed"))
            .tapError(e => ZIO.logDebug(s"$label - failed: $e"))
