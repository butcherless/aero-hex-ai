package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.RouteDto
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{IataCode, IcaoCode, Route, RouteId}
import dev.cmartin.aerohex.domain.port.in.{CreateRouteCommand, CreateRouteUseCase}
import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.{Scope, Task, ZIO, ZLayer}
import zio.test.*

import java.util.UUID

object RouteEndpointsSpec extends ZIOSpecDefault:

  private val routeId = RouteId(UUID.fromString("c2d3e4f5-a6b7-8901-cdef-012345678901"))
  private val route   = Route(routeId, IataCode("MAD"), IataCode("TFN"), IcaoCode("AEA"), 1740)

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultCreate: CreateRouteUseCase = (_: CreateRouteCommand) => ZIO.succeed(route)

  private val conflictCreate: CreateRouteUseCase =
    (cmd: CreateRouteCommand) => ZIO.fail(DomainError.RouteAlreadyExists(cmd.originIata, cmd.destinationIata))

  private val notFoundCreate: CreateRouteUseCase =
    (cmd: CreateRouteCommand) => ZIO.fail(DomainError.AirportNotFound(cmd.originIata))

  private val invalidCreate: CreateRouteUseCase =
    (_: CreateRouteCommand) => ZIO.fail(DomainError.InvalidRoute("origin and destination must differ"))

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(create: CreateRouteUseCase = defaultCreate): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new RouteRoutes(create).serverEndpoints)
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("RouteEndpoints")(
      suite("POST /api/v1/routes")(
        test("returns 201 with the created route") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA","distanceKm":1740}""")
                .contentType("application/json")
                .response(asJson[RouteDto])
                .send(makeBackend())
            created   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Created,
            created.exists(_.originIata == "MAD")
          )
        },
        test("returns 409 when the route already exists") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 404 when the origin airport or airline is not found") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"XXX","destinationIata":"TFN","airlineIcao":"AEA","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend(create = notFoundCreate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the use case reports an invalid route") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"MAD","airlineIcao":"AEA","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend(create = invalidCreate))
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when originIata is not exactly 3 letters") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MA","destinationIata":"TFN","airlineIcao":"AEA","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when destinationIata is longer than 3 letters") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"TFNX","airlineIcao":"AEA","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when airlineIcao is shorter than 3 letters") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AE","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when distanceKm is less than 1") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA","distanceKm":0}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("RouteRoutes.layer")(
        test("wires the use case into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[RouteRoutes](_.serverEndpoints.size)
                               .provide(ZLayer.succeed(defaultCreate), RouteRoutes.layer)
          yield assertTrue(endpointCount == 1)
        }
      )
    )
