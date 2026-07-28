package dev.cmartin.aerohex.adapter.http.aircraft

import dev.cmartin.aerohex.domain.aircraft.*
import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
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

object AircraftEndpointsSpec extends ZIOSpecDefault:

  private val ecMig = Aircraft(Registration("EC-MIG"), "B788", "Boeing 787-8", AirlineIcaoCode("IBE"))
  private val ecAbc = Aircraft(Registration("EC-ABC"), "A320", "Airbus A320", AirlineIcaoCode("VLG"))

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindAircraftUseCase = new FindAircraftUseCase:
    def findByRegistration(registration: String): IO[DomainError, Aircraft] = ZIO.succeed(ecMig)
    def findAll(p: Pagination): IO[DomainError, List[Aircraft]]             = ZIO.succeed(List(ecMig, ecAbc))

  private val notFoundFind: FindAircraftUseCase = new FindAircraftUseCase:
    def findByRegistration(registration: String): IO[DomainError, Aircraft] =
      ZIO.fail(DomainError.AircraftNotFound(registration))
    def findAll(p: Pagination): IO[DomainError, List[Aircraft]]             =
      ZIO.fail(DomainError.AircraftNotFound("n/a"))

  private val defaultCreate: CreateAircraftUseCase = (_: CreateAircraftCommand) => ZIO.succeed(ecMig)

  private val conflictCreate: CreateAircraftUseCase =
    (cmd: CreateAircraftCommand) => ZIO.fail(DomainError.AircraftAlreadyExists(cmd.registration.value))

  private val airlineNotFoundCreate: CreateAircraftUseCase =
    (cmd: CreateAircraftCommand) => ZIO.fail(DomainError.AirlineNotFound(cmd.airlineIcao.value))

  private val defaultUpdate: UpdateAircraftUseCase =
    (cmd: UpdateAircraftCommand) => ZIO.succeed(ecMig.copy(typeCode = cmd.typeCode))

  private val notFoundUpdate: UpdateAircraftUseCase =
    (cmd: UpdateAircraftCommand) => ZIO.fail(DomainError.AircraftNotFound(cmd.registration.value))

  private val airlineNotFoundUpdate: UpdateAircraftUseCase =
    (cmd: UpdateAircraftCommand) => ZIO.fail(DomainError.AirlineNotFound(cmd.airlineIcao.value))

  private val defaultDelete: DeleteAircraftUseCase = (_: Registration) => ZIO.unit

  private val notFoundDelete: DeleteAircraftUseCase =
    (reg: Registration) => ZIO.fail(DomainError.AircraftNotFound(reg.value))

  private val defaultFindByAirline: FindAircraftByAirlineUseCase =
    (_: AirlineIcaoCode, _: Pagination) => ZIO.succeed(List(ecMig))

  private val emptyFindByAirline: FindAircraftByAirlineUseCase =
    (_: AirlineIcaoCode, _: Pagination) => ZIO.succeed(Nil)

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
      find: FindAircraftUseCase = defaultFind,
      create: CreateAircraftUseCase = defaultCreate,
      update: UpdateAircraftUseCase = defaultUpdate,
      delete: DeleteAircraftUseCase = defaultDelete,
      findByAirline: FindAircraftByAirlineUseCase = defaultFindByAirline,
      tokenService: TokenService = validToken
  ): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(
        new AircraftRoutes(find, create, update, delete, findByAirline, tokenService).serverEndpoints
      )
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AircraftEndpoints")(
      suite("Authentication")(
        test("returns 401 when the Authorization header is missing") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/aircraft").send(makeBackend())
          yield assertTrue(response.code == StatusCode.Unauthorized)
        },
        test("returns 401 when the token is rejected") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/aircraft")
                          .send(makeBackend(tokenService = rejectingToken))
          yield assertTrue(response.code == StatusCode.Unauthorized)
        }
      ),
      suite("GET /api/v1/aircraft")(
        test("returns 200 with the full aircraft list") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/aircraft")
                          .response(asJson[List[AircraftDto]])
                          .send(makeBackend())
            aircraft  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(
            response.code == StatusCode.Ok,
            aircraft.map(_.registration) == List("EC-MIG", "EC-ABC")
          )
        },
        test("accepts custom page and pageSize query params") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/aircraft?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/aircraft")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/aircraft?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/aircraft?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- authedRequest.get(uri"https://test.com/api/v1/aircraft?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/aircraft/{registration}")(
        test("returns 200 with the requested aircraft") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/aircraft/EC-MIG")
                          .response(asJson[AircraftDto])
                          .send(makeBackend())
            found     = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            found.exists(_.registration == "EC-MIG")
          )
        },
        test("returns 404 when the aircraft does not exist") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/aircraft/N00000")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the registration is longer than 10 characters") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/aircraft/EXTREMELYLONGREG")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("POST /api/v1/aircraft")(
        test("returns 201 with a Location header pointing to the new resource") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/aircraft")
                .body(
                  """{"registration":"EC-MIG","typeCode":"B788","description":"Boeing 787-8","airlineIcao":"IBE"}"""
                )
                .contentType("application/json")
                .response(asJson[AircraftDto])
                .send(makeBackend())
            aircraft  = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Created,
            response.headers.exists(h => h.name.equalsIgnoreCase("Location") && h.value.contains("EC-MIG")),
            aircraft.exists(_.registration == "EC-MIG")
          )
        },
        test("returns 409 when the aircraft already exists") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/aircraft")
                .body(
                  """{"registration":"EC-MIG","typeCode":"B788","description":"Boeing 787-8","airlineIcao":"IBE"}"""
                )
                .contentType("application/json")
                .send(makeBackend(create = conflictCreate))
          yield assertTrue(response.code == StatusCode.Conflict)
        },
        test("returns 404 when the referenced airline does not exist") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/aircraft")
                .body(
                  """{"registration":"EC-MIG","typeCode":"B788","description":"Boeing 787-8","airlineIcao":"XXX"}"""
                )
                .contentType("application/json")
                .send(makeBackend(create = airlineNotFoundCreate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the registration is empty") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/aircraft")
                .body("""{"registration":"","typeCode":"B788","description":"Boeing 787-8","airlineIcao":"IBE"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the registration in the create body is longer than 10 characters") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/aircraft")
                .body(
                  """{"registration":"EXTREMELYLONGREG","typeCode":"B788","description":"Boeing 787-8","airlineIcao":"IBE"}"""
                )
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test(
          "returns 400 when registration contains a newline (real Registration.make check, not the length-only schema validator)"
        ) {
          // "A\nB" is 3 chars — within the schema's minLength(1)/maxLength(10) bounds — but the
          // domain assertion "^.{1,10}$".r doesn't match newlines, so only Registration.make itself
          // (not Tapir's schema validator) rejects it, exercising CreateAircraftRequest.toCommand's
          // orElseFail(InvalidRegistration(...)) branch.
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/aircraft")
                .body("""{"registration":"A\nB","typeCode":"B788","description":"Boeing 787-8","airlineIcao":"IBE"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when description is empty") {
          for
            response <-
              authedRequest
                .post(uri"https://test.com/api/v1/aircraft")
                .body("""{"registration":"EC-MIG","typeCode":"B788","description":"","airlineIcao":"IBE"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("PUT /api/v1/aircraft/{registration}")(
        test("returns 200 with the updated aircraft") {
          for
            response <-
              authedRequest
                .put(uri"https://test.com/api/v1/aircraft/EC-MIG")
                .body("""{"typeCode":"B789","description":"Boeing 787-9","airlineIcao":"IBE"}""")
                .contentType("application/json")
                .response(asJson[AircraftDto])
                .send(makeBackend())
            aircraft  = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            aircraft.exists(_.typeCode == "B789")
          )
        },
        test("returns 404 when the aircraft does not exist") {
          for
            response <-
              authedRequest
                .put(uri"https://test.com/api/v1/aircraft/XXX")
                .body("""{"typeCode":"B789","description":"Nowhere","airlineIcao":"IBE"}""")
                .contentType("application/json")
                .send(makeBackend(update = notFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 404 when the referenced airline does not exist") {
          for
            response <-
              authedRequest
                .put(uri"https://test.com/api/v1/aircraft/EC-MIG")
                .body("""{"typeCode":"B789","description":"Boeing 787-9","airlineIcao":"XXX"}""")
                .contentType("application/json")
                .send(makeBackend(update = airlineNotFoundUpdate))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the request body is invalid") {
          for
            response <-
              authedRequest
                .put(uri"https://test.com/api/v1/aircraft/EC-MIG")
                .body("""{"typeCode":"B789","description":"","airlineIcao":"IBE"}""")
                .contentType("application/json")
                .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("DELETE /api/v1/aircraft/{registration}")(
        test("returns 204 on successful deletion") {
          for
            response <- authedRequest.delete(uri"https://test.com/api/v1/aircraft/EC-MIG").send(makeBackend())
          yield assertTrue(response.code == StatusCode.NoContent)
        },
        test("returns 404 when the aircraft does not exist") {
          for
            response <- authedRequest
                          .delete(uri"https://test.com/api/v1/aircraft/XXX")
                          .send(makeBackend(delete = notFoundDelete))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when the registration is longer than 10 characters") {
          for
            response <- authedRequest
                          .delete(uri"https://test.com/api/v1/aircraft/EXTREMELYLONGREG")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/airlines/{icao}/aircraft")(
        test("returns 200 with the airline's aircraft") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines/IBE/aircraft")
                          .response(asJson[List[AircraftDto]])
                          .send(makeBackend())
            aircraft  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(response.code == StatusCode.Ok, aircraft.map(_.registration) == List("EC-MIG"))
        },
        test("returns 200 with an empty list for an airline with no aircraft") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines/VLG/aircraft")
                          .response(asJson[List[AircraftDto]])
                          .send(makeBackend(findByAirline = emptyFindByAirline))
            aircraft  = response.body.toOption.getOrElse(Nil)
          yield assertTrue(response.code == StatusCode.Ok, aircraft.isEmpty)
        },
        test("returns 400 when the ICAO code is not 3 letters") {
          for
            response <- authedRequest
                          .get(uri"https://test.com/api/v1/airlines/IB/aircraft")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 401 when the Authorization header is missing") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/airlines/IBE/aircraft").send(makeBackend())
          yield assertTrue(response.code == StatusCode.Unauthorized)
        }
      ),
      suite("AircraftRoutes.layer")(
        test("wires all five use cases into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[AircraftRoutes](_.serverEndpoints.size)
                               .provide(
                                 ZLayer.succeed(defaultFind),
                                 ZLayer.succeed(defaultCreate),
                                 ZLayer.succeed(defaultUpdate),
                                 ZLayer.succeed(defaultDelete),
                                 ZLayer.succeed(defaultFindByAirline),
                                 ZLayer.succeed(validToken),
                                 AircraftRoutes.layer
                               )
          yield assertTrue(endpointCount == 6)
        }
      )
    )
