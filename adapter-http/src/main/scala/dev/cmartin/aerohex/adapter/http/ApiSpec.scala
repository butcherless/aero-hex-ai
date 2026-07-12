package dev.cmartin.aerohex.adapter.http

import dev.cmartin.aerohex.adapter.http.aircraft.AircraftEndpoints
import dev.cmartin.aerohex.adapter.http.airline.AirlineEndpoints
import dev.cmartin.aerohex.adapter.http.airport.AirportEndpoints
import dev.cmartin.aerohex.adapter.http.country.CountryEndpoints
import dev.cmartin.aerohex.adapter.http.flight.{FlightEndpoints, FlightInstanceEndpoints}
import dev.cmartin.aerohex.adapter.http.route.RouteEndpoints
import sttp.apispec.Tag as ApiTag
import sttp.apispec.openapi.{Contact, Info, License}
import sttp.tapir.AnyEndpoint

object ApiSpec:

  val info: Info = Info(
    title = "Aviation Hexagonal API",
    version = "0.1.0",
    description =
      Some("REST API for managing countries, airports, airlines, routes, aircraft, flights, and flight instances."),
    contact = Some(Contact(name = Some("Aviation API Team"), email = Some("api@aviation.example"))),
    license = Some(License(name = "Apache 2.0", url = Some("https://www.apache.org/licenses/LICENSE-2.0")))
  )

  val topLevelTags: List[ApiTag] = List(
    ApiTag("Countries", description = Some("Country lookup operations.")),
    ApiTag("Airports", description = Some("Airport lookup operations.")),
    ApiTag("Airlines", description = Some("Airline lookup operations.")),
    ApiTag("Routes", description = Some("Flight route management operations.")),
    ApiTag("Aircraft", description = Some("Aircraft lookup operations.")),
    ApiTag("Flights", description = Some("Scheduled flight lookup operations.")),
    ApiTag(
      "Flight Instances",
      description = Some("Flight instance (actual, dated flight occurrence) lookup operations.")
    )
  )

  val allEndpoints: List[AnyEndpoint] = List(
    CountryEndpoints.findAll,
    CountryEndpoints.findByCode,
    CountryEndpoints.create,
    CountryEndpoints.update,
    CountryEndpoints.delete,
    AirportEndpoints.findAll,
    AirportEndpoints.searchByName,
    AirportEndpoints.findByIata,
    AirportEndpoints.create,
    AirportEndpoints.update,
    AirportEndpoints.delete,
    AirportEndpoints.findByCountry,
    AirlineEndpoints.findAll,
    AirlineEndpoints.findByIcao,
    AirlineEndpoints.create,
    AirlineEndpoints.update,
    AirlineEndpoints.delete,
    RouteEndpoints.create,
    AircraftEndpoints.findAll,
    AircraftEndpoints.findByRegistration,
    AircraftEndpoints.create,
    AircraftEndpoints.update,
    AircraftEndpoints.delete,
    FlightEndpoints.findAll,
    FlightEndpoints.findByCode,
    FlightInstanceEndpoints.findAll,
    FlightInstanceEndpoints.findById
  )
