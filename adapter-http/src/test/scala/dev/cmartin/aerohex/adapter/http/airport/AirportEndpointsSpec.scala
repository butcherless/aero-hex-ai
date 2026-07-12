package dev.cmartin.aerohex.adapter.http.airport

import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.*
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
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

object AirportEndpointsSpec extends ZIOSpecDefault:

  private val madrid    = Airport(IataCode("MAD"), IcaoCode("LEMD"), "Adolfo Suárez Madrid-Barajas", "Madrid")
  private val barcelona =
    Airport(IataCode("BCN"), IcaoCode("LEBL"), "Josep Tarradellas Barcelona-El Prat", "Barcelona")

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindAirportUseCase = new FindAirportUseCase:
    def findByIata(iata: String): IO[DomainError, Airport]      = ZIO.succeed(madrid)
    def findAll(p: Pagination): IO[DomainError, List[Airport]]  = ZIO.succeed(List(madrid, barcelona))
    def searchByName(q: String): IO[DomainError, List[Airport]] = ZIO.succeed(List(madrid))

  private val notFoundFind: FindAirportUseCase = new FindAirportUseCase:
    def findByIata(iata: String): IO[DomainError, Airport]      = ZIO.fail(DomainError.AirportNotFound(iata))
    def findAll(p: Pagination): IO[DomainError, List[Airport]]  = ZIO.fail(DomainError.AirportNotFound("n/a"))
    def searchByName(q: String): IO[DomainError, List[Airport]] = ZIO.fail(DomainError.AirportNotFound("n/a"))

  private val defaultCreate: CreateAirportUseCase = (_: CreateAirportCommand) => ZIO.succeed(madrid)

  private val conflictCreate: CreateAirportUseCase =
    (cmd: CreateAirportCommand) => ZIO.fail(DomainError.AirportAlreadyExists(cmd.iataCode.value))

  private val countryNotFoundCreate: CreateAirportUseCase =
    (cmd: CreateAirportCommand) => ZIO.fail(DomainError.CountryNotFound(cmd.countryCode.value))

  private val defaultFindByCountry: FindAirportsByCountryUseCase =
    (_: CountryCode, _: Pagination) => ZIO.succeed(List(madrid))

  private val countryNotFoundFindByCountry: FindAirportsByCountryUseCase =
    (code: CountryCode, _: Pagination) => ZIO.fail(DomainError.CountryNotFound(code.value))

  private val defaultUpdate: UpdateAirportUseCase =
    (cmd: UpdateAirportCommand) => ZIO.succeed(madrid.copy(name = cmd.name))

  private val notFoundUpdate: UpdateAirportUseCase =
    (cmd: UpdateAirportCommand) => ZIO.fail(DomainError.AirportNotFound(cmd.iataCode.value))

  private val countryNotFoundUpdate: UpdateAirportUseCase =
    (cmd: UpdateAirportCommand) => ZIO.fail(DomainError.CountryNotFound(cmd.countryCode.value))

  private val defaultDelete: DeleteAirportUseCase = (_: IataCode) => ZIO.unit

  private val notFoundDelete: DeleteAirportUseCase =
    (iata: IataCode) => ZIO.fail(DomainError.AirportNotFound(iata.value))

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(
      find: FindAirportUseCase = defaultFind,
      create: CreateAirportUseCase = defaultCreate,
      findByCountry: FindAirportsByCountryUseCase = defaultFindByCountry,
      update: UpdateAirportUseCase = defaultUpdate,
      delete: DeleteAirportUseCase = defaultDelete
  ): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new AirportRoutes(find, create, findByCountry, update, delete).serverEndpoints)
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AirportEndpoints")(
      suite("GET /api/v1/airports")(
        test("returns 200 with the full airport list") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airports")
                          .response(asJson[List[AirportDto]])
                          .send(makeBackend())
            airports  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            airports.map(_.iata) == List("MAD", "BCN")
          )
        },
        test("accepts custom page and pageSize query params") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airports?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airports")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airports?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airports?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airports?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/airports/search")(
        test("returns 200 with matching airports") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airports/search?q=Madrid")
                          .response(asJson[List[AirportDto]])
                          .send(makeBackend())
            airports  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            airports.map(_.iata) == List("MAD")
          )
        },
        test("returns 400 when query is shorter than 3 characters") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airports/search?q=ab")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airports/search?q=Madrid")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("GET /api/v1/airports/{iata}")(
        test("returns 200 with the requested airport") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airports/MAD")
                          .response(asJson[AirportDto])
                          .send(makeBackend())
            airport   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            airport.exists(_.iata == "MAD")
          )
        },
        test("returns 404 when the airport does not exist") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/airports/XXX")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the iata code is not exactly 3 letters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airports/MA").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the iata code is longer than 3 letters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airports/MADX").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the iata code contains non-alpha characters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airports/123").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("PUT /api/v1/airports/{iata}")(
        test("returns 200 with the updated airport") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airports/MAD")
                .body(
                  """{"icaoCode":"LEMD","name":"Madrid-Barajas Adolfo Suárez","city":"Madrid","countryCode":"ES"}"""
                )
                .contentType("application/json")
                .response(asJson[AirportDto])
                .send(makeBackend())
            airport   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            airport.exists(_.name == "Madrid-Barajas Adolfo Suárez")
          )
        },
        test("returns 404 when the airport does not exist") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airports/XXX")
                .body("""{"icaoCode":"LEMD","name":"Nowhere","city":"Nowhere","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend(update = notFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 404 when the referenced country does not exist") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airports/MAD")
                .body("""{"icaoCode":"LEMD","name":"Madrid","city":"Madrid","countryCode":"XX"}""")
                .contentType("application/json")
                .send(makeBackend(update = countryNotFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the iata code is not exactly 3 letters") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airports/MA")
                .body("""{"icaoCode":"LEMD","name":"Madrid","city":"Madrid","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the request body is invalid") {
          for
            response <-
              basicRequest
                .put(uri"https://test.com/api/v1/airports/MAD")
                .body("""{"icaoCode":"L","name":"Madrid","city":"Madrid","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("POST /api/v1/airports")(
        test("returns 201 with a Location header pointing to the new resource") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airports")
                .body(
                  """{"iata":"MAD","icaoCode":"LEMD","name":"Adolfo Suárez Madrid-Barajas","city":"Madrid","countryCode":"ES"}"""
                )
                .contentType("application/json")
                .response(asJson[AirportDto])
                .send(makeBackend())
            airport   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Created,
            response.headers.exists(h => h.name.equalsIgnoreCase("Location") && h.value.contains("MAD")),
            airport.exists(_.iata == "MAD")
          )
        },
        test("returns 409 when the airport already exists") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airports")
                .body("""{"iata":"MAD","icaoCode":"LEMD","name":"Madrid-Barajas","city":"Madrid","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 404 when the referenced country does not exist") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airports")
                .body("""{"iata":"MAD","icaoCode":"LEMD","name":"Madrid-Barajas","city":"Madrid","countryCode":"XX"}""")
                .contentType("application/json")
                .send(makeBackend(create = countryNotFoundCreate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the request body is invalid") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airports")
                .body("""{"iata":"M","icaoCode":"LEMD","name":"Madrid-Barajas","city":"Madrid","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when iata is 3 chars but not alphabetic (real IataCode.make check, not a stub)") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airports")
                .body("""{"iata":"123","icaoCode":"LEMD","name":"Nowhere","city":"Nowhere","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when icaoCode is 4 chars but not alphabetic (real IcaoCode.make check, not a stub)") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airports")
                .body("""{"iata":"MAD","icaoCode":"1234","name":"Nowhere","city":"Nowhere","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when name is empty") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airports")
                .body("""{"iata":"MAD","icaoCode":"LEMD","name":"","city":"Madrid","countryCode":"ES"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when city is empty") {
          for
            response <-
              basicRequest
                .post(uri"https://test.com/api/v1/airports")
                .body(
                  """{"iata":"MAD","icaoCode":"LEMD","name":"Adolfo Suárez Madrid-Barajas","city":"","countryCode":"ES"}"""
                )
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/countries/{code}/airports")(
        test("returns 200 with the airports in the country") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries/ES/airports")
                          .response(asJson[List[AirportDto]])
                          .send(makeBackend())
            airports  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            airports.map(_.iata) == List("MAD")
          )
        },
        test("returns 404 when the country does not exist") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries/XX/airports")
                          .send(makeBackend(findByCountry = countryNotFoundFindByCountry))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is shorter than 2 characters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/countries/X/airports").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code is longer than 2 characters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/countries/ESP/airports").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code contains non-alpha characters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/countries/12/airports").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <-
              basicRequest.get(uri"https://test.com/api/v1/countries/ES/airports?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <-
              basicRequest.get(uri"https://test.com/api/v1/countries/ES/airports?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/countries/ES/airports?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("DELETE /api/v1/airports/{iata}")(
        test("returns 204 on successful deletion") {
          for
            response <- basicRequest.delete(uri"https://test.com/api/v1/airports/MAD").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 404 when the airport does not exist") {
          for
            response <- basicRequest
                          .delete(uri"https://test.com/api/v1/airports/XXX")
                          .send(makeBackend(delete = notFoundDelete))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the iata code is not exactly 3 letters") {
          for
            response <- basicRequest.delete(uri"https://test.com/api/v1/airports/MA").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("AirportRoutes.layer")(
        test("wires all five use cases into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[AirportRoutes](_.serverEndpoints.size)
                               .provide(
                                 ZLayer.succeed(defaultFind),
                                 ZLayer.succeed(defaultCreate),
                                 ZLayer.succeed(defaultFindByCountry),
                                 ZLayer.succeed(defaultUpdate),
                                 ZLayer.succeed(defaultDelete),
                                 AirportRoutes.layer
                               )
          yield assertTrue(endpointCount == 7)
        }
      )
    )
