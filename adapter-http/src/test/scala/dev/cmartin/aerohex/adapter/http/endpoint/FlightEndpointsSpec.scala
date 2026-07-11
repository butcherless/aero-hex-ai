package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.FlightDto
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Flight, FlightCode, IcaoCode, RouteId}
import dev.cmartin.aerohex.domain.port.in.FindFlightUseCase
import dev.cmartin.aerohex.shared.Pagination
import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.{IO, Scope, Task, ZIO, ZLayer}
import zio.test.*

import java.time.LocalTime
import java.util.UUID

object FlightEndpointsSpec extends ZIOSpecDefault:

  private val routeId = RouteId(UUID.fromString("a0b1c2d3-e4f5-6789-abcd-ef0123456789"))

  private val flightWithAlias =
    Flight(FlightCode("UX9117"), Some("AEA9117"), LocalTime.of(7, 5), LocalTime.of(8, 55), routeId, IcaoCode("AEA"))

  private val flightNoAlias =
    Flight(FlightCode("VY1234"), None, LocalTime.of(10, 0), LocalTime.of(11, 30), routeId, IcaoCode("VLG"))

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindFlightUseCase = new FindFlightUseCase:
    def findByCode(code: String): IO[DomainError, Flight]     = ZIO.succeed(flightWithAlias)
    def findAll(p: Pagination): IO[DomainError, List[Flight]] = ZIO.succeed(List(flightWithAlias, flightNoAlias))

  private val notFoundFind: FindFlightUseCase = new FindFlightUseCase:
    def findByCode(code: String): IO[DomainError, Flight]     = ZIO.fail(DomainError.FlightNotFound(code))
    def findAll(p: Pagination): IO[DomainError, List[Flight]] = ZIO.fail(DomainError.FlightNotFound("n/a"))

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(find: FindFlightUseCase = defaultFind): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new FlightRoutes(find).serverEndpoints)
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FlightEndpoints")(
      suite("GET /api/v1/flights")(
        test("returns 200 with the full flight list, including flights with and without an alias") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flights")
                          .response(asJson[List[FlightDto]])
                          .send(makeBackend())
            flights   = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            flights.map(_.code) == List("UX9117", "VY1234"),
            flights.head.alias.contains("AEA9117"),
            flights(1).alias.isEmpty
          )
        },
        test("accepts custom page and pageSize query params") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flights?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flights")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("GET /api/v1/flights/{code}")(
        test("returns 200 with the requested flight") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flights/UX9117")
                          .response(asJson[FlightDto])
                          .send(makeBackend())
            flight    = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            flight.exists(_.code == "UX9117")
          )
        },
        test("returns 404 when the flight does not exist") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flights/XXXXXX")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("FlightRoutes.layer")(
        test("wires the use case into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[FlightRoutes](_.serverEndpoints.size)
                               .provide(ZLayer.succeed(defaultFind), FlightRoutes.layer)
          yield assertTrue(endpointCount == 2)
        }
      )
    )
