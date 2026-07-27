package dev.cmartin.aerohex.adapter.http.auth

import sttp.tapir.{Schema, Validator}

case class LoginRequest(username: String, password: String)

object LoginRequest {
  given Schema[LoginRequest] = Schema.derived[LoginRequest]
    .modify(_.username)(
      _.description("Login username.").validate(Validator.minLength(1)).encodedExample("admin")
    )
    .modify(_.password)(_.description("Login password.").validate(Validator.minLength(1)))
}

case class TokenResponse(token: String, tokenType: String, expiresIn: Int)

object TokenResponse {
  given Schema[TokenResponse] = Schema.derived[TokenResponse]
    .modify(_.token)(_.description("Signed JWT access token."))
    .modify(_.tokenType)(_.description("""Token type, always "Bearer".""").encodedExample("Bearer"))
    .modify(_.expiresIn)(_.description("Seconds until the token expires.").encodedExample(3600))
}
