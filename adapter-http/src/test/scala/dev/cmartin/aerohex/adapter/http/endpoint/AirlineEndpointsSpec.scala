package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airline, IcaoCode}
import dev.cmartin.aerohex.domain.port.in.*
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

import java.time.LocalDate

object AirlineEndpointsSpec extends ZIOSpecDefault:

  private val iberia  = Airline(IcaoCode("IBE"), "Iberia", LocalDate.of(1927, 6, 28))
  private val vueling = Airline(IcaoCode("VLG"), "Vueling", LocalDate.of(2004, 3, 1))

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindAirlineUseCase = new FindAirlineUseCase:
    def findByIcao(icao: String): IO[DomainError, Airline]     = ZIO.succeed(iberia)
    def findAll(p: Pagination): IO[DomainError, List[Airline]] = ZIO.succeed(List(iberia, vueling))

  private val notFoundFind: FindAirlineUseCase = new FindAirlineUseCase:
    def findByIcao(icao: String): IO[DomainError, Airline]     = ZIO.fail(DomainError.AirlineNotFound(icao))
    def findAll(p: Pagination): IO[DomainError, List[Airline]] = ZIO.fail(DomainError.AirlineNotFound("n/a"))

  private val defaultCreate: CreateAirlineUseCase = (_: CreateAirlineCommand) => ZIO.succeed(iberia)

  private val conflictCreate: CreateAirlineUseCase =
    (cmd: CreateAirlineCommand) => ZIO.fail(DomainError.AirlineAlreadyExists(cmd.icao.value))

  private val countryNotFoundCreate: CreateAirlineUseCase =
    (cmd: CreateAirlineCommand) => ZIO.fail(DomainError.CountryNotFound(cmd.countryCode.value))

  private val defaultUpdate: UpdateAirlineUseCase =
    (cmd: UpdateAirlineCommand) => ZIO.succeed(iberia.copy(name = cmd.name))

  private val notFoundUpdate: UpdateAirlineUseCase =
    (cmd: UpdateAirlineCommand) => ZIO.fail(DomainError.AirlineNotFound(cmd.icao.value))

  private val countryNotFoundUpdate: UpdateAirlineUseCase =
    (cmd: UpdateAirlineCommand) => ZIO.fail(DomainError.CountryNotFound(cmd.countryCode.value))

  private val defaultDelete: DeleteAirlineUseCase = (_: IcaoCode) => ZIO.unit

  private val notFoundDelete: DeleteAirlineUseCase =
    (icao: IcaoCode) => ZIO.fail(DomainError.AirlineNotFound(icao.value))

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(
      find: FindAirlineUseCase = defaultFind,
      create: CreateAirlineUseCase = defaultCreate,
      update: UpdateAirlineUseCase = defaultUpdate,
      delete: DeleteAirlineUseCase = defaultDelete
  ): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new AirlineRoutes(find, create, update, delete).serverEndpoints)
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AirlineEndpoints")(
      suite("GET /api/v1/airlines")(
        test("returns 200 with the full airline list") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airlines")
                          .response(asJson[List[AirlineDto]])
                          .send(makeBackend())
            airlines  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            airlines.map(_.icao) == List("IBE", "VLG")
          )
        },
        test("accepts custom page and pageSize query params") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airlines?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airlines")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airlines?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airlines?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airlines?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/airlines/{icao}")(
        test("returns 200 with the requested airline") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airlines/IBE")
                          .response(asJson[AirlineDto])
                          .send(makeBackend())
            airline   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            airline.exists(_.icao == "IBE")
          )
        },
        test("returns 404 when the airline does not exist") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airlines/XXX")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the icao code is not exactly 3 letters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airlines/IB").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the icao code is longer than 3 letters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airlines/IBEX").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the icao code contains non-alpha characters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airlines/123").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("POST /api/v1/airlines")(
        test("returns 201 with a Location header pointing to the new resource") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"IBE","name":"Iberia","foundationDate":"1927-06-28","countryCode":"ES"}""")
                .contentType("application/json")
                .response(asJson[AirlineDto])
                .send(makeBackend())
            airline   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Created,
            response.headers.exists(h => h.name.equalsIgnoreCase("Location") && h.value.contains("IBE")),
            airline.exists(_.icao == "IBE")
          )
        },
        test("returns 409 when the airline already exists") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"IBE","name":"Iberia","foundationDate":"1927-06-28","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 404 when the referenced country does not exist") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"IBE","name":"Iberia","foundationDate":"1927-06-28","countryCode":"XX"}""")
                .contentType("application/json")
                .send(makeBackend(create = countryNotFoundCreate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the icao code is not exactly 3 letters") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"I","name":"Iberia","foundationDate":"1927-06-28","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when name is empty") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"IBE","name":"","foundationDate":"1927-06-28","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("PUT /api/v1/airlines/{icao}")(
        test("returns 200 with the updated airline") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airlines/IBE")
                .body("""{"name":"Iberia Airlines","foundationDate":"1927-06-28","countryCode":"ES"}""")
                .contentType("application/json")
                .response(asJson[AirlineDto])
                .send(makeBackend())
            airline   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            airline.exists(_.name == "Iberia Airlines")
          )
        },
        test("returns 404 when the airline does not exist") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airlines/XXX")
                .body("""{"name":"Nowhere","foundationDate":"1927-06-28","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend(update = notFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 404 when the referenced country does not exist") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airlines/IBE")
                .body("""{"name":"Iberia","foundationDate":"1927-06-28","countryCode":"XX"}""")
                .contentType("application/json")
                .send(makeBackend(update = countryNotFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the icao code is not exactly 3 letters") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airlines/IB")
                .body("""{"name":"Iberia","foundationDate":"1927-06-28","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the request body is invalid") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airlines/IBE")
                .body("""{"name":"","foundationDate":"1927-06-28","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("DELETE /api/v1/airlines/{icao}")(
        test("returns 204 on successful deletion") {
          for
            response <- basicRequest.delete(uri"https://test.com/api/v1/airlines/IBE").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 404 when the airline does not exist") {
          for
            response <- basicRequest
                          .delete(uri"https://test.com/api/v1/airlines/XXX")
                          .send(makeBackend(delete = notFoundDelete))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the icao code is not exactly 3 letters") {
          for
            response <- basicRequest.delete(uri"https://test.com/api/v1/airlines/IB").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("AirlineRoutes.layer")(
        test("wires all four use cases into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[AirlineRoutes](_.serverEndpoints.size)
                               .provide(
                                 ZLayer.succeed(defaultFind),
                                 ZLayer.succeed(defaultCreate),
                                 ZLayer.succeed(defaultUpdate),
                                 ZLayer.succeed(defaultDelete),
                                 AirlineRoutes.layer
                               )
          yield assertTrue(endpointCount == 5)
        }
      )
    )
