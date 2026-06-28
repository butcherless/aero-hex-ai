package bootstrap

import adapter.http.server.HttpServer
import zio.*
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.logInfo("Aviation Hexagonal starting (API-dev mode)") *>
      HttpServer.serve.provide(WiringModule.appLayer)
}
