package dev.cmartin.aerohex.adapter.http.health

import java.io.PrintWriter
import java.lang.reflect.Proxy
import java.sql.{Connection, SQLException}
import java.util.logging.Logger
import javax.sql.DataSource
import sttp.client4.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.test.*
import zio.{Scope, Task, ZIO, ZLayer}

object HealthEndpointsSpec extends ZIOSpecDefault:

  // No mocking library exists in this project — java.sql.Connection has ~60 abstract methods,
  // too wide to hand-implement, so this uses the standard JDK dynamic-proxy trick instead: only
  // the two methods HealthRoutes actually calls (isValid, close) are special-cased.
  private val fakeConnection: Connection =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[Connection]),
        (_, method, _) =>
          method.getName match
            case "isValid" => java.lang.Boolean.TRUE
            case "close"   => null
            case _         => null
      )
      .asInstanceOf[Connection]

  // javax.sql.DataSource has a small surface, so it's implemented directly rather than via proxy.
  private val workingDataSource: DataSource = new DataSource:
    def getConnection(): Connection                                   = fakeConnection
    def getConnection(username: String, password: String): Connection = fakeConnection
    def getLogWriter(): PrintWriter                                   = null
    def setLogWriter(out: PrintWriter): Unit                          = ()
    def setLoginTimeout(seconds: Int): Unit                           = ()
    def getLoginTimeout(): Int                                        = 0
    def getParentLogger(): Logger                                     = throw new UnsupportedOperationException()
    def unwrap[T](iface: Class[T]): T                                 = throw new UnsupportedOperationException()
    def isWrapperFor(iface: Class[?]): Boolean                        = false

  private val failingDataSource: DataSource = new DataSource:
    def getConnection(): Connection                                   = throw new SQLException("connection refused")
    def getConnection(username: String, password: String): Connection =
      throw new SQLException("connection refused")
    def getLogWriter(): PrintWriter                                   = null
    def setLogWriter(out: PrintWriter): Unit                          = ()
    def setLoginTimeout(seconds: Int): Unit                           = ()
    def getLoginTimeout(): Int                                        = 0
    def getParentLogger(): Logger                                     = throw new UnsupportedOperationException()
    def unwrap[T](iface: Class[T]): T                                 = throw new UnsupportedOperationException()
    def isWrapperFor(iface: Class[?]): Boolean                        = false

  // HealthRoutes wires a DataSource into Tapir server endpoints.
  // TapirStubInterpreter runs the full decoded → logic → encode pipeline without a real HTTP server.
  private def makeBackend(dataSource: DataSource): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new HealthRoutes(dataSource).serverEndpoints)
      .backend()

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("HealthEndpoints")(
      suite("GET /health/live")(
        test("returns 200 regardless of DB state") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/health/live")
                          .send(makeBackend(failingDataSource))
          yield assertTrue(response.code == StatusCode.Ok)
        }
      ),
      suite("GET /health/ready")(
        test("returns 200 when Postgres is reachable") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/health/ready")
                          .send(makeBackend(workingDataSource))
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("returns 503 when Postgres is unreachable") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/health/ready")
                          .send(makeBackend(failingDataSource))
          yield assertTrue(response.code == StatusCode.ServiceUnavailable)
        }
      ),
      suite("HealthRoutes.layer")(
        test("wires both endpoints into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[HealthRoutes](_.serverEndpoints.size)
                               .provide(ZLayer.succeed(workingDataSource), HealthRoutes.layer)
          yield assertTrue(endpointCount == 2)
        }
      )
    )
