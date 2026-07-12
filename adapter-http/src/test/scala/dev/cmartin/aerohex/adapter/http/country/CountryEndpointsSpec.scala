package dev.cmartin.aerohex.adapter.http.country

import dev.cmartin.aerohex.domain.country.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.test.*
import zio.{IO, Scope, Task, UIO, ZIO, ZLayer}

object CountryEndpointsSpec extends ZIOSpecDefault:

  private val spain   = Country(CountryCode("ES"), "Spain")
  private val germany = Country(CountryCode("DE"), "Germany")

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindCountryUseCase = new FindCountryUseCase:
    def findByCode(code: CountryCode): IO[DomainError, Country] = ZIO.succeed(spain)
    def findAll(p: Pagination): UIO[List[Country]]              = ZIO.succeed(List(spain, germany))
    def searchByName(q: String): UIO[List[Country]]             = ZIO.succeed(List(spain))

  private val notFoundFind: FindCountryUseCase = new FindCountryUseCase:
    def findByCode(code: CountryCode): IO[DomainError, Country] = ZIO.fail(DomainError.CountryNotFound(code.value))
    def findAll(p: Pagination): UIO[List[Country]]              = ZIO.succeed(Nil)
    def searchByName(q: String): UIO[List[Country]]             = ZIO.succeed(Nil)

  private val defaultCreate: CreateCountryUseCase = (_: CreateCountryCommand) => ZIO.succeed(spain)

  private val conflictCreate: CreateCountryUseCase =
    (cmd: CreateCountryCommand) => ZIO.fail(DomainError.CountryAlreadyExists(cmd.code.value))

  private val invalidCodeCreate: CreateCountryUseCase =
    (cmd: CreateCountryCommand) => ZIO.fail(DomainError.InvalidCountryCode(cmd.code.value))

  private val defaultUpdate: UpdateCountryUseCase =
    (cmd: UpdateCountryCommand) => ZIO.succeed(spain.copy(name = cmd.name))

  private val notFoundUpdate: UpdateCountryUseCase =
    (cmd: UpdateCountryCommand) => ZIO.fail(DomainError.CountryNotFound(cmd.code.value))

  private val defaultDelete: DeleteCountryUseCase = (_: CountryCode) => ZIO.unit

  private val notFoundDelete: DeleteCountryUseCase =
    (code: CountryCode) => ZIO.fail(DomainError.CountryNotFound(code.value))

  // ── Backend factory ────────────────────────────────────────────────────────
  // CountryRoutes wires use-case stubs into Tapir server endpoints.
  // TapirStubInterpreter runs the full decoded → logic → encode pipeline
  // without a real HTTP server.

  private def makeBackend(
      find: FindCountryUseCase = defaultFind,
      create: CreateCountryUseCase = defaultCreate,
      update: UpdateCountryUseCase = defaultUpdate,
      delete: DeleteCountryUseCase = defaultDelete
  ): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new CountryRoutes(find, create, update, delete).serverEndpoints)
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("CountryEndpoints")(
      suite("GET /api/v1/countries")(
        test("returns 200 with the full country list") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries")
                          .response(asJson[List[CountryDto]])
                          .send(makeBackend())
            countries = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            countries.map(_.code) == List("ES", "DE")
          )
        },
        test("accepts custom page and pageSize query params") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("returns 400 when page is not an integer") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries?page=notanumber")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 200 with matching countries when name is provided") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries?name=Spa")
                          .response(asJson[List[CountryDto]])
                          .send(makeBackend())
            countries = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            countries.map(_.code) == List("ES")
          )
        },
        test("returns 400 when name is shorter than 3 characters") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries?name=ab")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries?pageSize=0")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries?pageSize=101")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries?page=0")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/countries/{code}")(
        test("returns 200 with the requested country") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries/ES")
                          .response(asJson[CountryDto])
                          .send(makeBackend())
            country   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            country.exists(_.code == "ES")
          )
        },
        test("returns 404 when the country does not exist") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/countries/XX")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is shorter than 2 characters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/countries/X").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code is longer than 2 characters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/countries/ESP").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code contains non-alpha characters") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/countries/12").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("POST /api/v1/countries")(
        test("returns 201 with a Location header pointing to the new resource") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"ES","name":"Spain"}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(
            response.code == StatusCode.Created,
            response.headers.exists(h => h.name.equalsIgnoreCase("Location") && h.value.contains("ES"))
          )
        },
        test("returns 409 when the country already exists") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"ES","name":"Spain"}""")
                          .contentType("application/json")
                          .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 400 when the code is not a real ISO 3166-1 alpha-2 country code") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"ZZ","name":"Nowhere"}""")
                          .contentType("application/json")
                          .send(makeBackend(create = invalidCodeCreate))
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the request body is invalid") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"E","name":"Spain"}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code is 2 chars but not alphabetic (real CountryCode.make check, not a stub)") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"12","name":"Nowhere"}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when name is empty") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"ES","name":""}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("PUT /api/v1/countries/{code}")(
        test("returns 200 with the updated country") {
          for
            response <- basicRequest
                          .put(uri"https://test.com/api/v1/countries/ES")
                          .body("""{"name":"Kingdom of Spain"}""")
                          .contentType("application/json")
                          .response(asJson[CountryDto])
                          .send(makeBackend())
            country   = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            country.exists(_.name == "Kingdom of Spain")
          )
        },
        test("returns 404 when the country does not exist") {
          for
            response <- basicRequest
                          .put(uri"https://test.com/api/v1/countries/XX")
                          .body("""{"name":"Nowhere"}""")
                          .contentType("application/json")
                          .send(makeBackend(update = notFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is shorter than 2 characters") {
          for
            response <- basicRequest
                          .put(uri"https://test.com/api/v1/countries/X")
                          .body("""{"name":"Spain"}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("DELETE /api/v1/countries/{code}")(
        test("returns 204 on successful deletion") {
          for
            response <- basicRequest.delete(uri"https://test.com/api/v1/countries/ES").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 404 when the country does not exist") {
          for
            response <- basicRequest
                          .delete(uri"https://test.com/api/v1/countries/XX")
                          .send(makeBackend(delete = notFoundDelete))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is shorter than 2 characters") {
          for
            response <- basicRequest.delete(uri"https://test.com/api/v1/countries/X").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("CountryRoutes.layer")(
        test("wires all four use cases into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[CountryRoutes](_.serverEndpoints.size)
                               .provide(
                                 ZLayer.succeed(defaultFind),
                                 ZLayer.succeed(defaultCreate),
                                 ZLayer.succeed(defaultUpdate),
                                 ZLayer.succeed(defaultDelete),
                                 CountryRoutes.layer
                               )
          yield assertTrue(endpointCount == 5)
        }
      )
    )
