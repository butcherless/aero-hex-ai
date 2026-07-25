package dev.cmartin.aerohex.adapter.http.health

import io.circe.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object HealthEndpoints {

  // No shared `base` here (unlike every other resource) — these two paths are deliberately
  // unversioned (no "api"/"v1" prefix), matching the k8s/load-balancer probe convention rather
  // than this project's REST API versioning.

  // Liveness does no I/O and genuinely cannot fail, so it has no error output at all — unlike
  // every other endpoint in this project, it skips the usual EndpointErrors.unexpectedError
  // safety-net variant, since documenting zero error responses is the honest OpenAPI contract
  // for a liveness probe.
  val live: PublicEndpoint[Unit, Unit, HealthStatus, Any] =
    endpoint.get
      .in("health" / "live")
      .summary("Liveness probe")
      .description("Always returns 200 if the process is running. Checks no dependencies.")
      .tag("Health")
      .out(jsonBody[HealthStatus].example(HealthStatus("UP")))

  // Readiness fails with a single fixed status code (503), not a DomainError-derived failure, so
  // the entity error vocabulary (HttpErrorResponse/ErrorMapper/oneOf variants) doesn't apply here
  // — the error body reuses HealthStatus instead.
  val ready: PublicEndpoint[Unit, HealthStatus, HealthStatus, Any] =
    endpoint.get
      .in("health" / "ready")
      .summary("Readiness probe")
      .description("Returns 200 if Postgres is reachable, 503 otherwise.")
      .tag("Health")
      .out(jsonBody[HealthStatus].example(HealthStatus("UP")))
      .errorOut(
        statusCode(StatusCode.ServiceUnavailable)
          .and(jsonBody[HealthStatus].example(HealthStatus("DOWN")))
      )
}
