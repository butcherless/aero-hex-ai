package dev.cmartin.aerohex.adapter.http.error

import dev.cmartin.aerohex.domain.error.DomainError
import sttp.model.StatusCode
import zio.Scope
import zio.test.*

object ErrorMapperSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ErrorMapper")(
      suite("toHttpError")(
        test("maps CountryNotFound to 404") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.CountryNotFound("XX"))
          assertTrue(status == StatusCode.NotFound, body.message.contains("XX"))
        },
        test("maps CountryAlreadyExists to 409") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.CountryAlreadyExists("ES"))
          assertTrue(status == StatusCode.Conflict, body.message.contains("ES"))
        },
        test("maps AirportNotFound to 404") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.AirportNotFound("MAD"))
          assertTrue(status == StatusCode.NotFound, body.message.contains("MAD"))
        },
        test("maps AirportAlreadyExists to 409") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.AirportAlreadyExists("MAD"))
          assertTrue(status == StatusCode.Conflict, body.message.contains("MAD"))
        },
        test("maps AirlineNotFound to 404") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.AirlineNotFound("IBE"))
          assertTrue(status == StatusCode.NotFound, body.message.contains("IBE"))
        },
        test("maps AirlineAlreadyExists to 409") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.AirlineAlreadyExists("IBE"))
          assertTrue(status == StatusCode.Conflict, body.message.contains("IBE"))
        },
        test("maps RouteNotFound to 404") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.RouteNotFound("route-1"))
          assertTrue(status == StatusCode.NotFound, body.message.contains("route-1"))
        },
        test("maps AircraftNotFound to 404") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.AircraftNotFound("EC-MIG"))
          assertTrue(status == StatusCode.NotFound, body.message.contains("EC-MIG"))
        },
        test("maps FlightNotFound to 404") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.FlightNotFound("UX9117"))
          assertTrue(status == StatusCode.NotFound, body.message.contains("UX9117"))
        },
        test("maps FlightInstanceNotFound to 404") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.FlightInstanceNotFound("fi-1"))
          assertTrue(status == StatusCode.NotFound, body.message.contains("fi-1"))
        },
        test("maps RouteAlreadyExists to 409") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.RouteAlreadyExists("MAD", "TFN"))
          assertTrue(status == StatusCode.Conflict, body.message.contains("MAD"), body.message.contains("TFN"))
        },
        test("maps InvalidRoute to 400 using the reason as the message") {
          val (status, body) = ErrorMapper.toHttpError(DomainError.InvalidRoute("origin and destination must differ"))
          assertTrue(status == StatusCode.BadRequest, body.message == "origin and destination must differ")
        }
      ),
      suite("toMessage")(
        test("returns the same message as toHttpError's body") {
          val error = DomainError.CountryNotFound("XX")
          assertTrue(ErrorMapper.toMessage(error) == ErrorMapper.toHttpError(error)._2.message)
        }
      )
    )
