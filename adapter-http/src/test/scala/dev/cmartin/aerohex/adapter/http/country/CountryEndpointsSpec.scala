package dev.cmartin.aerohex.adapter.http.country

import dev.cmartin.aerohex.adapter.http.error.HttpErrorResponse
import dev.cmartin.aerohex.domain.country.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{AccessToken, TokenService}
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
    def findAllUnbounded: UIO[List[Country]]                    = ZIO.succeed(List(spain, germany))
    def searchByName(q: String): UIO[List[Country]]             = ZIO.succeed(List(spain))

  private val notFoundFind: FindCountryUseCase = new FindCountryUseCase:
    def findByCode(code: CountryCode): IO[DomainError, Country] = ZIO.fail(DomainError.CountryNotFound(code.value))
    def findAll(p: Pagination): UIO[List[Country]]              = ZIO.succeed(Nil)
    def findAllUnbounded: UIO[List[Country]]                    = ZIO.succeed(Nil)
    def searchByName(q: String): UIO[List[Country]]             = ZIO.succeed(Nil)

  private val defaultCreate: CreateCountryUseCase = (_: CreateCountryCommand) => ZIO.succeed(spain)

  private val conflictCreate: CreateCountryUseCase =
    (cmd: CreateCountryCommand) => ZIO.fail(DomainError.CountryAlreadyExists(cmd.code.value))

  private val invalidCodeCreate: CreateCountryUseCase =
    (cmd: CreateCountryCommand) => ZIO.fail(DomainError.InvalidCountryCode(List(s"${cmd.code.value} is not real")))

  private val defaultUpdate: UpdateCountryUseCase =
    (cmd: UpdateCountryCommand) => ZIO.succeed(spain.copy(name = cmd.name))

  private val notFoundUpdate: UpdateCountryUseCase =
    (cmd: UpdateCountryCommand) => ZIO.fail(DomainError.CountryNotFound(cmd.code.value))

  private val defaultDelete: DeleteCountryUseCase = (_: CountryCode) => ZIO.unit

  private val notFoundDelete: DeleteCountryUseCase =
    (code: CountryCode) => ZIO.fail(DomainError.CountryNotFound(code.value))

  // Every endpoint now requires a bearer token (plans/security/protect-endpoints.md) — these two
  // stubs stand in for the real JwtService: validToken always succeeds, rejectingToken always
  // fails, regardless of the token string actually sent (that string-vs-signature distinction is
  // JwtServiceSpec's job, not this file's).
  private val validToken: TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]     = ZIO.die(new NotImplementedError("generate"))
    def validate(token: String): IO[DomainError, String] = ZIO.succeed("test-user")

  private val rejectingToken: TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]     = ZIO.die(new NotImplementedError("generate"))
    def validate(token: String): IO[DomainError, String] = ZIO.fail(DomainError.InvalidToken("rejected"))

  // Every pre-existing test needs this Authorization header now, or it fails with 401 instead of
  // its expected status — see the "missing header" test below for the one path that doesn't want it.
  private val authedRequest = basicRequest.header("Authorization", "Bearer test-token")

  // ── Backend factory ────────────────────────────────────────────────────────
  // CountryRoutes wires use-case stubs into Tapir server endpoints.
  // TapirStubInterpreter runs the full decoded → logic → encode pipeline
  // without a real HTTP server.

  private def makeBackend(
      find: FindCountryUseCase = defaultFind,
      create: CreateCountryUseCase = defaultCreate,
      update: UpdateCountryUseCase = defaultUpdate,
      delete: DeleteCountryUseCase = defaultDelete,
      tokenService: TokenService = validToken
  ): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new CountryRoutes(find, create, update, delete, tokenService).serverEndpoints)
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("CountryEndpoints")(
      suite("Authentication")(
        test("returns 401 when the Authorization header is missing") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/countries").send(makeBackend())
          yield assertTrue(response.code == StatusCode.Unauthorized)
        },
        test("returns 401 when the token is rejected") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries")
                          .send(makeBackend(tokenService = rejectingToken))
          yield assertTrue(response.code == StatusCode.Unauthorized)
        }
      ),
      suite("GET /api/v1/countries")(
        test("returns 200 with the full country list") {
          for
            response <- authedRequest
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
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("returns 400 when page is not an integer") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries?page=notanumber")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 200 with matching countries when name is provided") {
          for
            response <- authedRequest
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
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries?name=ab")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries?pageSize=0")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries?pageSize=101")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries?page=0")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/countries/{code}")(
        test("returns 200 with the requested country") {
          for
            response <- authedRequest
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
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/countries/XX")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is shorter than 2 characters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/countries/X").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code is longer than 2 characters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/countries/ESP").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code contains non-alpha characters") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/countries/12").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("POST /api/v1/countries")(
        test("returns 201 with a Location header pointing to the new resource") {
          for
            response <- authedRequest
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
            response <- authedRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"ES","name":"Spain"}""")
                          .contentType("application/json")
                          .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 400 when the code is not a real ISO 3166-1 alpha-2 country code") {
          for
            response <- authedRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"ZZ","name":"Nowhere"}""")
                          .contentType("application/json")
                          .send(makeBackend(create = invalidCodeCreate))
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the request body is invalid") {
          for
            response <- authedRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"E","name":"Spain"}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the code is 2 chars but not alphabetic (real CountryCode.make check, not a stub)") {
          for
            response <- authedRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"12","name":"Nowhere"}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        // Tapir's own minLength(2)/maxLength(2) schema validator on `code` already rejects any
        // body whose code isn't exactly 2 characters *before* CreateCountryRequest.toCommand runs
        // — so a blank or wrong-length code never reaches CountryCode.validateAll through this
        // endpoint (that accumulation is instead covered directly by CountryCodeSpec). Only the
        // "letters only" rule can ever surface here, since Tapir has no shape/alphabetic check of
        // its own — this test proves the accumulated `errors` list reaches the JSON response body
        // end-to-end, not just the domain layer. asJsonAlways (not asJson) is required since sttp's
        // asJson only decodes 2xx responses; a 400 body needs the "always decode" variant.
        test("returns 400 with the accumulated errors list when the code is 2 chars but not alphabetic") {
          for
            response <- authedRequest
                          .post(uri"https://test.com/api/v1/countries")
                          .body("""{"code":"1A","name":"Nowhere"}""")
                          .contentType("application/json")
                          .response(asJsonAlways[HttpErrorResponse])
                          .send(makeBackend())
            body      = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.BadRequest,
            body.exists(_.errors == List("country code must contain only letters"))
          )
        },
        test("returns 400 when name is empty") {
          for
            response <- authedRequest
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
            response <- authedRequest
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
            response <- authedRequest
                          .put(uri"https://test.com/api/v1/countries/XX")
                          .body("""{"name":"Nowhere"}""")
                          .contentType("application/json")
                          .send(makeBackend(update = notFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is shorter than 2 characters") {
          for
            response <- authedRequest
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
            response <- authedRequest.delete(uri"https://test.com/api/v1/countries/ES").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 404 when the country does not exist") {
          for
            response <- authedRequest
                          .delete(uri"https://test.com/api/v1/countries/XX")
                          .send(makeBackend(delete = notFoundDelete))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the code is shorter than 2 characters") {
          for
            response <- authedRequest.delete(uri"https://test.com/api/v1/countries/X").send(makeBackend())
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
                                 ZLayer.succeed(validToken),
                                 CountryRoutes.layer
                               )
          yield assertTrue(endpointCount == 5)
        }
      )
    )
