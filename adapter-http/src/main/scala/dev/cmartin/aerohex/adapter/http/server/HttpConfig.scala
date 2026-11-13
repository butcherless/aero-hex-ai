package dev.cmartin.aerohex.adapter.http.server

import zio.Config

/** HTTP server settings, under `http` (`http.port`, env `HTTP_PORT`). */
final case class HttpConfig(port: Int)

object HttpConfig:
  val config: Config[HttpConfig] =
    Config.int("port").withDefault(8080).map(HttpConfig.apply).nested("http")
