package dev.cmartin.aerohex.adapter.http.middleware

import java.util.UUID
import zio.*
import zio.http.*

/** Generates (or reuses, if already present) a per-request trace id at the HTTP
  * boundary, makes it available to every ZIO.log* call for the duration of
  * request handling via the FiberRef-backed ZIO.logAnnotate mechanism, and
  * echoes it back to the client as a response header.
  */
object TraceIdMiddleware:

  val TraceIdHeaderName: String    = "X-Trace-Id"
  val TraceIdAnnotationKey: String = "traceId"

  def live(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any]:
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              val traceId = req.headers.get(TraceIdHeaderName).getOrElse(UUID.randomUUID().toString)
              ZIO
                .logAnnotate(LogAnnotation(TraceIdAnnotationKey, traceId)) {
                  h(req)
                }
                .map(_.addHeaders(Headers(TraceIdHeaderName, traceId)))
            }
          }
        }
