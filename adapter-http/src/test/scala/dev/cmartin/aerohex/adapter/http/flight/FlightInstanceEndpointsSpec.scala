package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.domain.aircraft.Registration
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{FindFlightInstanceUseCase, FlightCode, FlightInstance, FlightInstanceId}
import dev.cmartin.aerohex.shared.Pagination
import io.circe.generic.auto.*
import java.time.LocalDateTime
import java.util.UUID
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.test.*
import zio.{IO, Scope, Task, ZIO, ZLayer}

object FlightInstanceEndpointsSpec extends ZIOSpecDefault:

  private val instanceId = FlightInstanceId(UUID.fromString("b1c2d3e4-f5a6-7890-bcde-f01234567890"))

  private val instance = FlightInstance(
    instanceId,
    LocalDateTime.of(2024, 6, 28, 15, 23),
    LocalDateTime.of(2024, 6, 28, 19, 41),
    FlightCode("UX9117"),
    Registration("EC-MIG")
  )

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindFlightInstanceUseCase = new FindFlightInstanceUseCase:
    def findById(id: String): IO[DomainError, FlightInstance]         = ZIO.succeed(instance)
    def findAll(p: Pagination): IO[DomainError, List[FlightInstance]] = ZIO.succeed(List(instance))

  private val notFoundFind: FindFlightInstanceUseCase = new FindFlightInstanceUseCase:
    def findById(id: String): IO[DomainError, FlightInstance]         =
      ZIO.fail(DomainError.FlightInstanceNotFound(id))
    def findAll(p: Pagination): IO[DomainError, List[FlightInstance]] =
      ZIO.fail(DomainError.FlightInstanceNotFound("n/a"))

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(find: FindFlightInstanceUseCase = defaultFind): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new FlightInstanceRoutes(find).serverEndpoints)
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FlightInstanceEndpoints")(
      suite("GET /api/v1/flight-instances")(
        test("returns 200 with the full flight instance list") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flight-instances")
                          .response(asJson[List[FlightInstanceDto]])
                          .send(makeBackend())
            instances = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            instances.map(_.flightCode) == List("UX9117")
          )
        },
        test("accepts custom page and pageSize query params") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flight-instances?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flight-instances")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <-
              basicRequest.get(uri"https://test.com/api/v1/flight-instances?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <-
              basicRequest.get(uri"https://test.com/api/v1/flight-instances?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/flight-instances?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/flight-instances/{id}")(
        test("returns 200 with the requested flight instance") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flight-instances/b1c2d3e4-f5a6-7890-bcde-f01234567890")
                          .response(asJson[FlightInstanceDto])
                          .send(makeBackend())
            found     = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            found.exists(_.flightCode == "UX9117")
          )
        },
        test("returns 404 when the flight instance does not exist") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flight-instances/00000000-0000-0000-0000-000000000000")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the id is not a valid UUID") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/flight-instances/not-a-uuid").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("FlightInstanceRoutes.layer")(
        test("wires the use case into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[FlightInstanceRoutes](_.serverEndpoints.size)
                               .provide(ZLayer.succeed(defaultFind), FlightInstanceRoutes.layer)
          yield assertTrue(endpointCount == 2)
        }
      )
    )
