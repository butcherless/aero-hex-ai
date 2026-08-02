package dev.cmartin.aerohex.adapter.http.route

import dev.cmartin.aerohex.domain.route.{Route, RouteWithAirportNames}
import sttp.tapir.Schema
import sttp.tapir.Validator

// originAirportName/destinationAirportName are populated only by the list/search endpoint
// (`fromDomainWithNames`) — POST /api/v1/routes and GET /api/v1/airlines/{icao}/routes
// (`fromDomain`) don't have airport names available without a materially bigger change, and
// neither needed one; see plans/add-airport-names-to-route-list.md.
case class RouteDto(
    originIata: String,
    destinationIata: String,
    distanceKm: Int,
    originAirportName: Option[String],
    destinationAirportName: Option[String]
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

private val originAirportNameSchema: Schema[Option[String]] => Schema[Option[String]] = _.description(
  "Name of the origin airport. Only present on the list/search response."
)
  .encodedExample("Adolfo Suárez Madrid–Barajas Airport")

private val destinationAirportNameSchema: Schema[Option[String]] => Schema[Option[String]] = _.description(
  "Name of the destination airport. Only present on the list/search response."
)
  .encodedExample("Tenerife North Airport")

object RouteDto {
  def fromDomain(route: Route): RouteDto =
    RouteDto(
      originIata = route.origin.value,
      destinationIata = route.destination.value,
      distanceKm = route.distanceKm,
      originAirportName = None,
      destinationAirportName = None
    )

  def fromDomainWithNames(route: RouteWithAirportNames): RouteDto =
    RouteDto(
      originIata = route.route.origin.value,
      destinationIata = route.route.destination.value,
      distanceKm = route.route.distanceKm,
      originAirportName = Some(route.originAirportName),
      destinationAirportName = Some(route.destinationAirportName)
    )

  given Schema[RouteDto] = Schema.derived[RouteDto]
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.distanceKm)(distanceKmSchema)
    .modify(_.originAirportName)(originAirportNameSchema)
    .modify(_.destinationAirportName)(destinationAirportNameSchema)
}

object CreateRouteRequest {
  given Schema[CreateRouteRequest] = Schema.derived[CreateRouteRequest]
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.distanceKm)(distanceKmSchema)
}
