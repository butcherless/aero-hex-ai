package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.*
import dev.cmartin.aerohex.shared.Pagination
import io.circe.generic.auto.*
import java.time.LocalTime
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.test.*
import zio.{IO, Scope, Task, ZIO, ZLayer}

object FlightEndpointsSpec extends ZIOSpecDefault:

  private val flightWithAlias =
    Flight(
      FlightCode("UX9117"),
      Some("AEA9117"),
      LocalTime.of(7, 5),
      LocalTime.of(8, 55),
      IataCode("MAD"),
      IataCode("TFN"),
      IcaoCode("AEA")
    )

  private val flightNoAlias =
    Flight(
      FlightCode("VY1234"),
      None,
      LocalTime.of(10, 0),
      LocalTime.of(11, 30),
      IataCode("MAD"),
      IataCode("BCN"),
      IcaoCode("VLG")
    )

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindFlightUseCase = new FindFlightUseCase:
    def findByCode(code: String): IO[DomainError, Flight]     = ZIO.succeed(flightWithAlias)
    def findAll(p: Pagination): IO[DomainError, List[Flight]] = ZIO.succeed(List(flightWithAlias, flightNoAlias))

  private val notFoundFind: FindFlightUseCase = new FindFlightUseCase:
    def findByCode(code: String): IO[DomainError, Flight]     = ZIO.fail(DomainError.FlightNotFound(code))
    def findAll(p: Pagination): IO[DomainError, List[Flight]] = ZIO.fail(DomainError.FlightNotFound("n/a"))

  private val defaultCreate: CreateFlightUseCase = (_: CreateFlightCommand) => ZIO.succeed(flightWithAlias)

  private val conflictCreate: CreateFlightUseCase =
    (cmd: CreateFlightCommand) => ZIO.fail(DomainError.FlightAlreadyExists(cmd.code.value))

  private val notFoundCreate: CreateFlightUseCase =
    (cmd: CreateFlightCommand) => ZIO.fail(DomainError.AirportNotFound(cmd.origin.value))

  private val defaultUpdate: UpdateFlightUseCase =
    (cmd: UpdateFlightCommand) => ZIO.succeed(flightWithAlias.copy(schedDeparture = cmd.schedDeparture))

  private val notFoundUpdate: UpdateFlightUseCase =
    (cmd: UpdateFlightCommand) => ZIO.fail(DomainError.FlightNotFound(cmd.code.value))

  private val defaultDelete: DeleteFlightUseCase = (_: FlightCode) => ZIO.unit

  private val notFoundDelete: DeleteFlightUseCase =
    (code: FlightCode) => ZIO.fail(DomainError.FlightNotFound(code.value))

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(
      find: FindFlightUseCase = defaultFind,
      create: CreateFlightUseCase = defaultCreate,
      update: UpdateFlightUseCase = defaultUpdate,
      delete: DeleteFlightUseCase = defaultDelete
  ): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new FlightRoutes(find, create, update, delete).serverEndpoints)
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
        },
        test("returns 400 when pageSize is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/flights?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/flights?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/flights?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
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
        },
        test("returns 400 when the code is longer than 8 characters") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/flights/EXTREMELYLONGCODE")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("POST /api/v1/flights")(
        test("returns 201 with a Location header pointing to the new resource") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/flights")
                .body(
                  """{"code":"UX9117","alias":"AEA9117","schedDeparture":"07:05","schedArrival":"08:55",""" +
                    """"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA"}"""
                )
                .contentType("application/json")
                .response(asJson[FlightDto])
                .send(makeBackend())
            flight    = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Created,
            response.headers.exists(h => h.name.equalsIgnoreCase("Location") && h.value.contains("UX9117")),
            flight.exists(_.code == "UX9117")
          )
        },
        test("returns 409 when the flight already exists") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/flights")
                .body(
                  """{"code":"UX9117","alias":null,"schedDeparture":"07:05","schedArrival":"08:55",""" +
                    """"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA"}"""
                )
                .contentType("application/json")
                .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 404 when the referenced origin airport does not exist") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/flights")
                .body(
                  """{"code":"UX9117","alias":null,"schedDeparture":"07:05","schedArrival":"08:55",""" +
                    """"originIata":"XXX","destinationIata":"TFN","airlineIcao":"AEA"}"""
                )
                .contentType("application/json")
                .send(makeBackend(create = notFoundCreate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is empty (real FlightCode.make check, not a stub)") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/flights")
                .body(
                  """{"code":"","alias":null,"schedDeparture":"07:05","schedArrival":"08:55",""" +
                    """"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA"}"""
                )
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code in the create body is longer than 8 characters") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/flights")
                .body(
                  """{"code":"EXTREMELYLONGCODE","alias":null,"schedDeparture":"07:05","schedArrival":"08:55",""" +
                    """"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA"}"""
                )
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("PUT /api/v1/flights/{code}")(
        test("returns 200 with the updated flight") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/flights/UX9117")
                .body(
                  """{"alias":"AEA9117","schedDeparture":"07:30","schedArrival":"09:15",""" +
                    """"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA"}"""
                )
                .contentType("application/json")
                .response(asJson[FlightDto])
                .send(makeBackend())
            flight    = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            flight.exists(_.schedDeparture == "07:30")
          )
        },
        test("returns 404 when the flight does not exist") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/flights/XXXXXX")
                .body(
                  """{"alias":null,"schedDeparture":"07:30","schedArrival":"09:15",""" +
                    """"originIata":"MAD","destinationIata":"TFN","airlineIcao":"AEA"}"""
                )
                .contentType("application/json")
                .send(makeBackend(update = notFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("DELETE /api/v1/flights/{code}")(
        test("returns 204 on successful deletion") {
          for
            response <- basicRequest.delete(uri"https://test.com/api/v1/flights/UX9117").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 404 when the flight does not exist") {
          for
            response <- basicRequest
                          .delete(uri"https://test.com/api/v1/flights/XXXXXX")
                          .send(makeBackend(delete = notFoundDelete))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is longer than 8 characters") {
          for
            response <- basicRequest
                          .delete(uri"https://test.com/api/v1/flights/EXTREMELYLONGCODE")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("FlightRoutes.layer")(
        test("wires all four use cases into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[FlightRoutes](_.serverEndpoints.size)
                               .provide(
                                 ZLayer.succeed(defaultFind),
                                 ZLayer.succeed(defaultCreate),
                                 ZLayer.succeed(defaultUpdate),
                                 ZLayer.succeed(defaultDelete),
                                 FlightRoutes.layer
                               )
          yield assertTrue(endpointCount == 5)
        }
      )
    )
