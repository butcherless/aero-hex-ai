package dev.cmartin.aerohex.adapter.http.middleware

import zio.*
import zio.http.*
import zio.test.*

object TraceIdMiddlewareSpec extends ZIOSpecDefault:

  private def probeRoutes(captured: Ref[Option[String]]): Routes[Any, Response] =
    Routes(
      Method.GET / "probe" -> handler { (_: Request) =>
        ZIO.logAnnotations
          .flatMap(anns => captured.set(anns.get(TraceIdMiddleware.TraceIdAnnotationKey)))
          .as(Response.ok)
      }
    )

  private def run(request: Request): ZIO[Any, Nothing, (Response, Option[String])] =
    for
      captured <- Ref.make[Option[String]](None)
      routes    = probeRoutes(captured) @@ TraceIdMiddleware.live
      response <- ZIO.scoped(routes.runZIO(request))
      seen     <- captured.get
    yield (response, seen)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("TraceIdMiddleware")(
      test("generates a traceId, annotates the fiber's logs with it, and echoes it as a response header") {
        for
          result          <- run(Request.get("/probe"))
          (response, seen) = result
        yield assertTrue(
          response.headers.get(TraceIdMiddleware.TraceIdHeaderName).isDefined,
          seen.isDefined,
          seen == response.headers.get(TraceIdMiddleware.TraceIdHeaderName)
        )
      },
      test("reuses an incoming X-Trace-Id header instead of generating a new one") {
        val incoming = "client-supplied-id-123"
        val request  = Request.get("/probe").addHeader(TraceIdMiddleware.TraceIdHeaderName, incoming)
        for
          result          <- run(request)
          (response, seen) = result
        yield assertTrue(
          response.headers.get(TraceIdMiddleware.TraceIdHeaderName).contains(incoming),
          seen.contains(incoming)
        )
      },
      test("generates a different traceId on each request when none is supplied") {
        for
          firstResult  <- run(Request.get("/probe"))
          secondResult <- run(Request.get("/probe"))
          first         = firstResult._1
          second        = secondResult._1
        yield assertTrue(
          first.headers.get(TraceIdMiddleware.TraceIdHeaderName) !=
            second.headers.get(TraceIdMiddleware.TraceIdHeaderName)
        )
      }
    )
