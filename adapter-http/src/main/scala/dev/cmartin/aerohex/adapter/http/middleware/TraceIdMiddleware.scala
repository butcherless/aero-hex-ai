package dev.cmartin.aerohex.adapter.http.middleware

import java.util.UUID
import zio.*
import zio.http.*

/** Generates (or reuses, if already present and well-formed) a per-request
  * trace id at the HTTP boundary, makes it available to every ZIO.log* call for
  * the duration of request handling via the FiberRef-backed ZIO.logAnnotate
  * mechanism, and echoes it back to the client as a response header.
  */
object TraceIdMiddleware:

  val TraceIdHeaderName: String    = "X-Trace-Id"
  val TraceIdAnnotationKey: String = "traceId"

  // The incoming header comes from the client and ends up in every log line and in the response.
  // Only short ids made of letters, digits, '.', '_' and '-' are reused (UUIDs and W3C/B3 ids
  // fit). Anything else is replaced with a new UUID.
  private val AcceptedTraceId = "^[A-Za-z0-9._-]{1,64}$".r

  private[middleware] def isAcceptable(traceId: String): Boolean = AcceptedTraceId.matches(traceId)

  def live(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any]:
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              val traceId = req.headers
                .get(TraceIdHeaderName)
                .filter(isAcceptable)
                .getOrElse(UUID.randomUUID().toString)
              ZIO
                .logAnnotate(LogAnnotation(TraceIdAnnotationKey, traceId)) {
                  h(req)
                }
                .map(_.addHeaders(Headers(TraceIdHeaderName, traceId)))
            }
          }
        }
