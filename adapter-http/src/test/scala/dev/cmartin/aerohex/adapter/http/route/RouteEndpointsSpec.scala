package dev.cmartin.aerohex.adapter.http.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{AssociateAirlineUseCase, CreateRouteCommand, CreateRouteUseCase}
import dev.cmartin.aerohex.domain.route.{DisassociateAirlineUseCase, FindRoutesByAirlineUseCase, Route}
import dev.cmartin.aerohex.shared.Pagination
import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.test.*
import zio.{IO, Scope, Task, ZIO, ZLayer}

object RouteEndpointsSpec extends ZIOSpecDefault:

  private val route = Route(IataCode("MAD"), IataCode("TFN"), 1740)

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultCreate: CreateRouteUseCase = (_: CreateRouteCommand) => ZIO.succeed(route)

  private val conflictCreate: CreateRouteUseCase =
    (cmd: CreateRouteCommand) => ZIO.fail(DomainError.RouteAlreadyExists(cmd.originIata, cmd.destinationIata))

  private val notFoundCreate: CreateRouteUseCase =
    (cmd: CreateRouteCommand) => ZIO.fail(DomainError.AirportNotFound(cmd.originIata))

  private val invalidCreate: CreateRouteUseCase =
    (_: CreateRouteCommand) => ZIO.fail(DomainError.InvalidRoute("origin and destination must differ"))

  private val defaultAssociate: AssociateAirlineUseCase =
    (_: String, _: String, _: String) => ZIO.unit

  private val conflictAssociate: AssociateAirlineUseCase =
    (o: String, d: String, icao: String) => ZIO.fail(DomainError.RouteAirlineAlreadyExists(o, d, icao))

  private val notFoundAssociate: AssociateAirlineUseCase =
    (o: String, d: String, _: String) => ZIO.fail(DomainError.RouteNotFound(o, d))

  private val defaultDisassociate: DisassociateAirlineUseCase =
    (_: String, _: String, _: String) => ZIO.unit

  private val notFoundDisassociate: DisassociateAirlineUseCase =
    (o: String, d: String, icao: String) => ZIO.fail(DomainError.RouteAirlineNotFound(o, d, icao))

  private val defaultFindByAirline: FindRoutesByAirlineUseCase =
    (_: String, _: Pagination) => ZIO.succeed(List(route))

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(
      create: CreateRouteUseCase = defaultCreate,
      associate: AssociateAirlineUseCase = defaultAssociate,
      disassociate: DisassociateAirlineUseCase = defaultDisassociate,
      findByAirline: FindRoutesByAirlineUseCase = defaultFindByAirline
  ): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new RouteRoutes(create, associate, disassociate, findByAirline).serverEndpoints)
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
                .body("""{"originIata":"MAD","destinationIata":"TFN","distanceKm":1740}""")
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
                .body("""{"originIata":"MAD","destinationIata":"TFN","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 404 when the origin airport is not found") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"XXX","destinationIata":"TFN","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend(create = notFoundCreate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the use case reports an invalid route") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"MAD","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend(create = invalidCreate))
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when originIata is not exactly 3 letters") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MA","destinationIata":"TFN","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when destinationIata is longer than 3 letters") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"TFNX","distanceKm":1740}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when distanceKm is less than 1") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/routes")
                .body("""{"originIata":"MAD","destinationIata":"TFN","distanceKm":0}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("POST /api/v1/routes/{origin}/{destination}/airlines/{icao}")(
        test("returns 204 when the association succeeds") {
          for response <-
              basicRequest.post(uri"https://test.com/api/v1/routes/MAD/TFN/airlines/AEA").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 409 when the airline is already associated") {
          for response <- basicRequest
                            .post(uri"https://test.com/api/v1/routes/MAD/TFN/airlines/AEA")
                            .send(makeBackend(associate = conflictAssociate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 404 when the route does not exist") {
          for response <- basicRequest
                            .post(uri"https://test.com/api/v1/routes/XXX/TFN/airlines/AEA")
                            .send(makeBackend(associate = notFoundAssociate))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("DELETE /api/v1/routes/{origin}/{destination}/airlines/{icao}")(
        test("returns 204 when the disassociation succeeds") {
          for response <-
              basicRequest.delete(uri"https://test.com/api/v1/routes/MAD/TFN/airlines/AEA").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 404 when the airline is not associated with the route") {
          for response <- basicRequest
                            .delete(uri"https://test.com/api/v1/routes/MAD/TFN/airlines/AEA")
                            .send(makeBackend(disassociate = notFoundDisassociate))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("GET /api/v1/airlines/{icao}/routes")(
        test("returns 200 with the routes operated by the airline") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airlines/AEA/routes")
                          .response(asJson[List[RouteDto]])
                          .send(makeBackend())
            routes    = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            routes.exists(_.originIata == "MAD")
          )
        }
      ),
      suite("RouteRoutes.layer")(
        test("wires the use cases into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[RouteRoutes](_.serverEndpoints.size)
                               .provide(
                                 ZLayer.succeed(defaultCreate),
                                 ZLayer.succeed(defaultAssociate),
                                 ZLayer.succeed(defaultDisassociate),
                                 ZLayer.succeed(defaultFindByAirline),
                                 RouteRoutes.layer
                               )
          yield assertTrue(endpointCount == 4)
        }
      )
    )
