package dev.cmartin.aerohex.adapter.http.route

import dev.cmartin.aerohex.domain.route.Route
import sttp.tapir.Schema
import sttp.tapir.Validator

case class RouteDto(
    originIata: String,
    destinationIata: String,
    distanceKm: Int
)

case class CreateRouteRequest(
    originIata: String,
    destinationIata: String,
    distanceKm: Int
)

// Shared verbatim by RouteDto and CreateRouteRequest below.
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

private val distanceKmSchema: Schema[Int] => Schema[Int] =
  _.description("Flight distance in kilometres.").validate(Validator.min(1)).encodedExample(1740)

object RouteDto {
  def fromDomain(route: Route): RouteDto =
    RouteDto(
      originIata = route.origin.value,
      destinationIata = route.destination.value,
      distanceKm = route.distanceKm
    )

  given Schema[RouteDto] = Schema.derived[RouteDto]
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.distanceKm)(distanceKmSchema)
}

object CreateRouteRequest {
  given Schema[CreateRouteRequest] = Schema.derived[CreateRouteRequest]
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.distanceKm)(distanceKmSchema)
}
