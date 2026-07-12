package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.ApiSpec
import sttp.apispec.openapi.Server
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object OpenApiGenerator:
  def main(args: Array[String]): Unit =
    val yaml = OpenAPIDocsInterpreter()
      .toOpenAPI(ApiSpec.allEndpoints, ApiSpec.info)
      .tags(ApiSpec.topLevelTags)
      .servers(List(Server("http://localhost:8080", description = Some("Local development server"))))
      .copy(openapi = "3.1.2")
      .toYaml
    println(yaml)
