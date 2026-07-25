package dev.cmartin.aerohex.adapter.http.health

import java.sql.SQLException
import javax.sql.DataSource
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.{Duration, Task, URLayer, ZIO, ZLayer}

class HealthRoutes(dataSource: DataSource):

  // Offloaded onto ZIO's blocking executor since java.sql.Connection is a blocking API.
  // isValid(2) performs a real round-trip check within a 2s timeout — stronger than merely
  // obtaining/closing a pooled connection, since HikariCP doesn't validate on every checkout.
  // Bounded by a 3s timeoutFail: HikariCP's own connectionTimeout defaults to 30s when the pool
  // can't reach Postgres at all (as opposed to isValid's 2s, which only applies once a
  // connection is obtained) — without this, an unreachable DB makes /health/ready hang for 30s,
  // far past a typical k8s/load-balancer probe timeout. `.disconnect` is required because
  // attemptBlocking's underlying JDBC call can't actually be force-stopped by fiber interruption
  // (the JVM thread just keeps blocking) — without it, timeoutFail would await that blocking call
  // to finish before returning, defeating the timeout entirely.
  private val checkDb: Task[Unit] =
    ZIO
      .attemptBlocking {
        val conn = dataSource.getConnection()
        try if !conn.isValid(2) then throw new SQLException("Connection reported not valid")
        finally conn.close()
      }
      .disconnect
      .timeoutFail(new SQLException("Readiness check timed out"))(Duration.fromSeconds(3L))

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    HealthEndpoints.live.zServerLogic(_ => ZIO.succeed(HealthStatus("UP"))),
    HealthEndpoints.ready.zServerLogic { _ =>
      checkDb
        .tapError(e => ZIO.logWarning(s"Readiness check failed: ${e.getMessage}"))
        .as(HealthStatus("UP"))
        .orElseFail(HealthStatus("DOWN"))
    }
  )

object HealthRoutes:
  val layer: URLayer[DataSource, HealthRoutes] = ZLayer.fromFunction(new HealthRoutes(_))
