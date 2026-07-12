package dev.cmartin.aerohex.adapter.http.route

import dev.cmartin.aerohex.domain.route.Route
import sttp.tapir.Schema
import sttp.tapir.Validator

case class RouteDto(
    id: String,
    originIata: String,
    destinationIata: String,
    airlineIcao: String,
    distanceKm: Int
)

case class CreateRouteRequest(
    originIata: String,
    destinationIata: String,
    airlineIcao: String,
    distanceKm: Int
)

// Shared verbatim by RouteDto and CreateRouteRequest below — CreateRouteRequest is RouteDto
// minus the server-assigned id.
private val originIataSchema: Schema[String] => Schema[String] = _.description(
  "IATA code of the origin airport."
)
  .validate(Validator.minLength(3))
  .validate(Validator.maxLength(3))
  .encodedExample("MAD")

private val destinationIataSchema: Schema[String] => Schema[String] = _.description(
  "IATA code of the destination airport."
)
  .validate(Validator.minLength(3))
  .validate(Validator.maxLength(3))
  .encodedExample("TFN")

private val airlineIcaoSchema: Schema[String] => Schema[String] = _.description(
  "ICAO code of the operating airline."
)
  .validate(Validator.minLength(3))
  .validate(Validator.maxLength(3))
  .encodedExample("AEA")

private val distanceKmSchema: Schema[Int] => Schema[Int] =
  _.description("Flight distance in kilometres.").validate(Validator.min(1)).encodedExample(1740)

object RouteDto {
  def fromDomain(route: Route): RouteDto =
    RouteDto(
      id = route.id.value.toString,
      originIata = route.origin.value,
      destinationIata = route.destination.value,
      airlineIcao = route.airlineIcao.value,
      distanceKm = route.distanceKm
    )

  given Schema[RouteDto] = Schema.derived[RouteDto]
    .modify(_.id)(
      _.description("Unique route identifier.").format("uuid").encodedExample("c2d3e4f5-a6b7-8901-cdef-012345678901")
    )
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.airlineIcao)(airlineIcaoSchema)
    .modify(_.distanceKm)(distanceKmSchema)
}

object CreateRouteRequest {
  given Schema[CreateRouteRequest] = Schema.derived[CreateRouteRequest]
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.airlineIcao)(airlineIcaoSchema)
    .modify(_.distanceKm)(distanceKmSchema)
}
