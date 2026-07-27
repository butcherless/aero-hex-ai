package dev.cmartin.aerohex.adapter.http.auth

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{AccessToken, LoginUseCase}
import io.circe.generic.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub4.TapirStubInterpreter
import zio.test.*
import zio.{Scope, Task, ZIO, ZLayer}

object AuthEndpointsSpec extends ZIOSpecDefault:

  private val defaultLogin: LoginUseCase =
    (_: String, _: String) => ZIO.succeed(AccessToken("signed-jwt", 3600))

  private val invalidCredentialsLogin: LoginUseCase =
    (_: String, _: String) => ZIO.fail(DomainError.InvalidCredentials)

  private def makeBackend(login: LoginUseCase = defaultLogin): Backend[Task] =
    TapirStubInterpreter(BackendStub(new RIOMonadAsyncError[Any]))
      .whenServerEndpointsRunLogic(new AuthRoutes(login).serverEndpoints)
      .backend()

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AuthEndpoints")(
      suite("POST /api/v1/auth/login")(
        test("returns 200 with a token on valid credentials") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/auth/login")
                          .body("""{"username":"admin","password":"correct"}""")
                          .contentType("application/json")
                          .response(asJson[TokenResponse])
                          .send(makeBackend())
            body      = response.body.toOption
          yield assertTrue(
            response.code == StatusCode.Ok,
            body.exists(_.token == "signed-jwt"),
            body.exists(_.tokenType == "Bearer"),
            body.exists(_.expiresIn == 3600)
          )
        },
        test("returns 401 on invalid credentials") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/auth/login")
                          .body("""{"username":"admin","password":"wrong"}""")
                          .contentType("application/json")
                          .send(makeBackend(login = invalidCredentialsLogin))
          yield assertTrue(response.code == StatusCode.Unauthorized)
        },
        test("returns 400 when username is empty") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/auth/login")
                          .body("""{"username":"","password":"correct"}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("returns 400 when the request body is invalid") {
          for
            response <- basicRequest
                          .post(uri"https://test.com/api/v1/auth/login")
                          .body("""{"username":"admin"}""")
                          .contentType("application/json")
                          .send(makeBackend())
          yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ),
      suite("AuthRoutes.layer")(
        test("wires the login use case into the route list") {
          for
            endpointCount <- ZIO
                               .serviceWith[AuthRoutes](_.serverEndpoints.size)
                               .provide(ZLayer.succeed(defaultLogin), AuthRoutes.layer)
          yield assertTrue(endpointCount == 1)
        }
      )
    )
