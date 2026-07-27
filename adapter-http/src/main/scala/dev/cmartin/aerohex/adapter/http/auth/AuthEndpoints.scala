package dev.cmartin.aerohex.adapter.http.auth

import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import io.circe.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object AuthEndpoints {

  private val base = endpoint.in("api" / "v1" / "auth")

  val login: PublicEndpoint[LoginRequest, (StatusCode, HttpErrorResponse), TokenResponse, Any] =
    base.post
      .summary("Login")
      .description("Authenticates a user with a username/password pair and returns a signed JWT access token.")
      .tag("Auth")
      .in("login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[TokenResponse].description("The issued access token."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.unauthorizedVariant("Invalid credentials."),
          EndpointErrors.unexpectedError
        )
      )

  val logout: Endpoint[String, Unit, (StatusCode, HttpErrorResponse), Unit, Any] =
    base.post
      .securityIn(auth.bearer[String]())
      .summary("Logout")
      .description("Revokes the calling token, so it can no longer authenticate even before its natural expiry.")
      .tag("Auth")
      .in("logout")
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.unauthorizedVariant("Missing or invalid token."),
          EndpointErrors.unexpectedError
        )
      )
}
