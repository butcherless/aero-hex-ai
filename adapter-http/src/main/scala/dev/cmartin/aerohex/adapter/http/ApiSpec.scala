package dev.cmartin.aerohex.adapter.http

import dev.cmartin.aerohex.adapter.http.endpoint.*
import sttp.apispec.Tag as ApiTag
import sttp.apispec.openapi.{Contact, Info, License}
import sttp.tapir.AnyEndpoint

object ApiSpec:

  val info: Info = Info(
    title = "Aviation Hexagonal API",
    version = "0.1.0",
    description = Some("REST API for managing countries, airports, airlines, routes, aircraft, flights, and journeys."),
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
    ApiTag("Journeys", description = Some("Journey (actual flight instance) lookup operations."))
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
    AirportEndpoints.findByCountry,
    AirlineEndpoints.findAll,
    AirlineEndpoints.findByIcao,
    RouteEndpoints.create,
    AircraftEndpoints.findAll,
    AircraftEndpoints.findByRegistration,
    FlightEndpoints.findAll,
    FlightEndpoints.findByCode,
    JourneyEndpoints.findAll,
    JourneyEndpoints.findById
  )
