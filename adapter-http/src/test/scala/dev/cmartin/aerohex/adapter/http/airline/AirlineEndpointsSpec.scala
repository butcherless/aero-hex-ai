package dev.cmartin.aerohex.adapter.http.airline

import dev.cmartin.aerohex.domain.airline.*
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.FindAirlinesByRouteUseCase
import dev.cmartin.aerohex.domain.user.{AccessToken, TokenService, ValidatedToken}
import dev.cmartin.aerohex.shared.Pagination
import io.circe.generic.auto.*
import java.time.Instant
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.test.*
import zio.{IO, Scope, Task, UIO, ZIO, ZLayer}

object AirlineEndpointsSpec extends ZIOSpecDefault:

  private val iberia  = Airline(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA"))
  private val vueling = Airline(AirlineIcaoCode("VLG"), "Vueling", None, Some("VUELING"))

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindAirlineUseCase = new FindAirlineUseCase:
    def findByIcao(icao: String): IO[DomainError, Airline]                         = ZIO.succeed(iberia)
    def findAll(p: Pagination): IO[DomainError, List[Airline]]                     = ZIO.succeed(List(iberia, vueling))
    def findAllUnbounded: IO[DomainError, List[Airline]]                           = ZIO.succeed(List(iberia, vueling))
    def findAllUnboundedWithCountry: IO[DomainError, List[(Airline, CountryCode)]] =
      ZIO.die(new NotImplementedError("findAllUnboundedWithCountry"))
    def searchByName(q: String): IO[DomainError, List[Airline]]                    = ZIO.succeed(List(iberia))

  private val notFoundFind: FindAirlineUseCase = new FindAirlineUseCase:
    def findByIcao(icao: String): IO[DomainError, Airline]                         = ZIO.fail(DomainError.AirlineNotFound(icao))
    def findAll(p: Pagination): IO[DomainError, List[Airline]]                     = ZIO.fail(DomainError.AirlineNotFound("n/a"))
    def findAllUnbounded: IO[DomainError, List[Airline]]                           = ZIO.fail(DomainError.AirlineNotFound("n/a"))
    def findAllUnboundedWithCountry: IO[DomainError, List[(Airline, CountryCode)]] =
      ZIO.die(new NotImplementedError("findAllUnboundedWithCountry"))
    def searchByName(q: String): IO[DomainError, List[Airline]]                    = ZIO.fail(DomainError.AirlineNotFound("n/a"))

  private val defaultCreate: CreateAirlineUseCase = (_: CreateAirlineCommand) => ZIO.succeed(iberia)

  private val conflictCreate: CreateAirlineUseCase =
    (cmd: CreateAirlineCommand) => ZIO.fail(DomainError.AirlineAlreadyExists(cmd.icao.value))

  private val countryNotFoundCreate: CreateAirlineUseCase =
    (cmd: CreateAirlineCommand) => ZIO.fail(DomainError.CountryNotFound(cmd.countryCode.value))

  private val defaultFindByCountry: FindAirlinesByCountryUseCase =
    (_: CountryCode, _: Pagination) => ZIO.succeed(List(iberia))

  private val countryNotFoundFindByCountry: FindAirlinesByCountryUseCase =
    (code: CountryCode, _: Pagination) => ZIO.fail(DomainError.CountryNotFound(code.value))

  private val defaultUpdate: UpdateAirlineUseCase =
    (cmd: UpdateAirlineCommand) => ZIO.succeed(iberia.copy(name = cmd.name))

  private val notFoundUpdate: UpdateAirlineUseCase =
    (cmd: UpdateAirlineCommand) => ZIO.fail(DomainError.AirlineNotFound(cmd.icao.value))

  private val countryNotFoundUpdate: UpdateAirlineUseCase =
    (cmd: UpdateAirlineCommand) => ZIO.fail(DomainError.CountryNotFound(cmd.countryCode.value))

  private val defaultDelete: DeleteAirlineUseCase = (_: AirlineIcaoCode) => ZIO.unit

  private val notFoundDelete: DeleteAirlineUseCase =
    (icao: AirlineIcaoCode) => ZIO.fail(DomainError.AirlineNotFound(icao.value))

  private val defaultFindByRoute: FindAirlinesByRouteUseCase =
    (_: String, _: String) => ZIO.succeed(List(iberia))

  private val failingFindByRoute: FindAirlinesByRouteUseCase =
    (origin: String, destination: String) => ZIO.fail(DomainError.RouteNotFound(origin, destination))

  // Every endpoint now requires a bearer token (plans/security/protect-endpoints.md).
  private val validToken: TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]             = ZIO.die(new NotImplementedError("generate"))
    def validate(token: String): IO[DomainError, ValidatedToken] =
      ZIO.succeed(ValidatedToken("test-user", "test-jti", Instant.parse("2026-01-01T01:00:00Z")))
    def revoke(jti: String, expiresAt: Instant): UIO[Unit]       = ZIO.die(new NotImplementedError("revoke"))

  private val rejectingToken: TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]             = ZIO.die(new NotImplementedError("generate"))
    def validate(token: String): IO[DomainError, ValidatedToken] = ZIO.fail(DomainError.InvalidToken("rejected"))
    def revoke(jti: String, expiresAt: Instant): UIO[Unit]       = ZIO.die(new NotImplementedError("revoke"))

  private val authedRequest = basicRequest.header("Authorization", "Bearer test-token")

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(
      find: FindAirlineUseCase = defaultFind,
      create: CreateAirlineUseCase = defaultCreate,
      findByCountry: FindAirlinesByCountryUseCase = defaultFindByCountry,
      update: UpdateAirlineUseCase = defaultUpdate,
      delete: DeleteAirlineUseCase = defaultDelete,
      findByRoute: FindAirlinesByRouteUseCase = defaultFindByRoute,
      tokenService: TokenService = validToken
  ): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(
        new AirlineRoutes(find, create, findByCountry, update, delete, findByRoute, tokenService).serverEndpoints
      )
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AirlineEndpoints")(
      suite("Authentication")(
        test("returns 401 when the Authorization header is missing") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airlines").send(makeBackend())
          yield assertTrue(response.code == StatusCode.Unauthorized)
        },
        test("returns 401 when the token is rejected") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines")
                          .send(makeBackend(tokenService = rejectingToken))
          yield assertTrue(response.code == StatusCode.Unauthorized)
        }
      ),
      suite("GET /api/v1/airlines")(
        test("returns 200 with the full airline list") {
          for
            response <- authedRequest
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
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/airlines?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/airlines?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/airlines?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/airlines/search")(
        test("returns 200 with matching airlines") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines/search?q=Iberia")
                          .response(asJson[List[AirlineDto]])
                          .send(makeBackend())
            airlines  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            airlines.map(_.icao) == List("IBE")
          )
        },
        test("returns 400 when query is shorter than 3 characters") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines/search?q=ab")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines/search?q=Iberia")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("GET /api/v1/airlines/{icao}")(
        test("returns 200 with the requested airline") {
          for
            response <- authedRequest
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
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines/XXX")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the icao code is not exactly 3 letters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/airlines/IB").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the icao code is longer than 3 letters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/airlines/IBEX").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the icao code contains non-alpha characters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/airlines/123").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("POST /api/v1/airlines")(
        test("returns 201 with a Location header pointing to the new resource") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"IBE","name":"Iberia","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
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
              authedRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"IBE","name":"Iberia","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 404 when the referenced country does not exist") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"IBE","name":"Iberia","alias":null,"callsign":"IBERIA","countryCode":"XX"}""")
                .contentType("application/json")
                .send(makeBackend(create = countryNotFoundCreate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the icao code is not exactly 3 letters") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"I","name":"Iberia","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when icao is 3 chars but not alphabetic (real AirlineIcaoCode.make check, not a stub)") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"123","name":"Nowhere","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when name is empty") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/airlines")
                .body("""{"icao":"IBE","name":"","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("PUT /api/v1/airlines/{icao}")(
        test("returns 200 with the updated airline") {
          for
            response <-
              authedRequest
                .put(uri"https://test.com/api/v1/airlines/IBE")
                .body("""{"name":"Iberia Airlines","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
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
              authedRequest
                .put(uri"https://test.com/api/v1/airlines/XXX")
                .body("""{"name":"Nowhere","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend(update = notFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 404 when the referenced country does not exist") {
          for
            response <-
              authedRequest
                .put(uri"https://test.com/api/v1/airlines/IBE")
                .body("""{"name":"Iberia","alias":null,"callsign":"IBERIA","countryCode":"XX"}""")
                .contentType("application/json")
                .send(makeBackend(update = countryNotFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the icao code is not exactly 3 letters") {
          for
            response <-
              authedRequest
                .put(uri"https://test.com/api/v1/airlines/IB")
                .body("""{"name":"Iberia","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the request body is invalid") {
          for
            response <-
              authedRequest
                .put(uri"https://test.com/api/v1/airlines/IBE")
                .body("""{"name":"","alias":null,"callsign":"IBERIA","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/countries/{code}/airlines")(
        test("returns 200 with the airlines in the country") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries/ES/airlines")
                          .response(asJson[List[AirlineDto]])
                          .send(makeBackend())
            airlines  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            airlines.map(_.icao) == List("IBE")
          )
        },
        test("returns 404 when the country does not exist") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries/XX/airlines")
                          .send(makeBackend(findByCountry = countryNotFoundFindByCountry))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is shorter than 2 characters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/countries/X/airlines").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code is longer than 2 characters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/countries/ESP/airlines").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code contains non-alpha characters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/countries/12/airlines").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <-
              authedRequest.get(uri"https://test.com/api/v1/countries/ES/airlines?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <-
              authedRequest.get(uri"https://test.com/api/v1/countries/ES/airlines?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/countries/ES/airlines?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("DELETE /api/v1/airlines/{icao}")(
        test("returns 204 on successful deletion") {
          for
            response <- authedRequest.delete(uri"https://test.com/api/v1/airlines/IBE").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 404 when the airline does not exist") {
          for
            response <- authedRequest
                          .delete(uri"https://test.com/api/v1/airlines/XXX")
                          .send(makeBackend(delete = notFoundDelete))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the icao code is not exactly 3 letters") {
          for
            response <- authedRequest.delete(uri"https://test.com/api/v1/airlines/IB").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/routes/{origin}/{destination}/airlines")(
        test("returns 200 with the airlines operating the route") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/routes/MAD/TFN/airlines")
                          .response(asJson[List[AirlineDto]])
                          .send(makeBackend())
            airlines  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            airlines.map(_.icao) == List("IBE")
          )
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/routes/MAD/TFN/airlines")
                          .send(makeBackend(findByRoute = failingFindByRoute))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("AirlineRoutes.layer")(
        test("wires all six use cases into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[AirlineRoutes](_.serverEndpoints.size)
                               .provide(
                                 ZLayer.succeed(defaultFind),
                                 ZLayer.succeed(defaultCreate),
                                 ZLayer.succeed(defaultFindByCountry),
                                 ZLayer.succeed(defaultUpdate),
                                 ZLayer.succeed(defaultDelete),
                                 ZLayer.succeed(defaultFindByRoute),
                                 ZLayer.succeed(validToken),
                                 AirlineRoutes.layer
                               )
          yield assertTrue(endpointCount == 8)
        }
      )
    )
