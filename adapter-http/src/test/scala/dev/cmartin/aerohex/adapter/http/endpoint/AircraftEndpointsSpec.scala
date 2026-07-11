package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.AircraftDto
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Aircraft, IcaoCode, Registration}
import dev.cmartin.aerohex.domain.port.in.FindAircraftUseCase
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

object AircraftEndpointsSpec extends ZIOSpecDefault:

  private val ecMig = Aircraft(Registration("EC-MIG"), "B788", IcaoCode("IBE"))
  private val ecAbc = Aircraft(Registration("EC-ABC"), "A320", IcaoCode("VLG"))

  // ── Stub use-case implementations ─────────────────────────────────────────

  private val defaultFind: FindAircraftUseCase = new FindAircraftUseCase:
    def findByRegistration(registration: String): IO[DomainError, Aircraft] = ZIO.succeed(ecMig)
    def findAll(p: Pagination): IO[DomainError, List[Aircraft]]             = ZIO.succeed(List(ecMig, ecAbc))

  private val notFoundFind: FindAircraftUseCase = new FindAircraftUseCase:
    def findByRegistration(registration: String): IO[DomainError, Aircraft] =
      ZIO.fail(DomainError.AircraftNotFound(registration))
    def findAll(p: Pagination): IO[DomainError, List[Aircraft]]             =
      ZIO.fail(DomainError.AircraftNotFound("n/a"))

  // ── Backend factory ────────────────────────────────────────────────────────

  private def makeBackend(find: FindAircraftUseCase = defaultFind): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new AircraftRoutes(find).serverEndpoints)
      .backend()

  // ── Spec ──────────────────────────────────────────────────────────────────

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AircraftEndpoints")(
      suite("GET /api/v1/aircraft")(
        test("returns 200 with the full aircraft list") {
          for
            response <- basicRequest
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
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/aircraft?page=2&pageSize=5")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.Ok)
        },
        test("propagates the mapped domain error when the use case fails") {
          for
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/aircraft")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        },
        test("returns 400 when pageSize is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/aircraft?pageSize=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when pageSize is over 100") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/aircraft?pageSize=101").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when page is 0") {
          for
            response <- basicRequest.get(uri"https://test.com/api/v1/aircraft?page=0").send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("GET /api/v1/aircraft/{registration}")(
        test("returns 200 with the requested aircraft") {
          for
            response <- basicRequest
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
            response <- basicRequest
                          .get(uri"https://test.com/api/v1/aircraft/N00000")
                          .send(makeBackend(find = notFoundFind))
          yield assertTrue(response.code == StatusCode.NotFound)
        }
      ),
      suite("AircraftRoutes.layer")(
        test("wires the use case into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[AircraftRoutes](_.serverEndpoints.size)
                               .provide(ZLayer.succeed(defaultFind), AircraftRoutes.layer)
          yield assertTrue(endpointCount == 2)
        }
      )
    )
