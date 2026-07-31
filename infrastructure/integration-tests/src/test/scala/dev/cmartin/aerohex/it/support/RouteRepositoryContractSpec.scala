package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.airport.{Airport, AirportIcaoCode, AirportRepository, IataCode}
import dev.cmartin.aerohex.domain.country.{Country, CountryCode, CountryRepository}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{Route, RouteRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.ZIO
import zio.test.*

// Behavior contract for QuillRouteRepositoryItSpec, satisfying the RouteRepository port. Route
// depends on two Airports (origin + destination), each of which itself depends on Country, so
// every test seeds the full chain first.
object RouteRepositoryContractSpec:

  private def seedCountry(code: String, name: String): ZIO[CountryRepository, DomainError, Unit] =
    ZIO.serviceWithZIO[CountryRepository](_.save(Country(CountryCode.unsafeMake(code), name)).unit)

  private def seedAirport(
      iata: String,
      icao: String,
      name: String,
      city: String,
      countryCode: String
  ): ZIO[AirportRepository, DomainError, Unit] =
    ZIO.serviceWithZIO[AirportRepository](
      _.save(
        Airport(IataCode.unsafeMake(iata), AirportIcaoCode.unsafeMake(icao), name, city, 0, 0),
        CountryCode.unsafeMake(countryCode)
      ).unit
    )

  def tests: List[Spec[RouteRepository & AirportRepository & CountryRepository, Any]] = List(
    test("saves and finds a route by segment") {
      for
        _     <- seedCountry("ES", "Spain")
        _     <- seedAirport("MAD", "LEMD", "Barajas", "Madrid", "ES")
        _     <- seedAirport("TFN", "GCXO", "Norte", "Tenerife", "ES")
        repo  <- ZIO.service[RouteRepository]
        route  = Route(IataCode("MAD"), IataCode("TFN"), 1740)
        saved <- repo.save(route)
        found <- repo.findBySegment(IataCode("MAD"), IataCode("TFN"))
      yield assertTrue(saved == route, found.contains(route))
    },
    test("findBySegment returns None for an unknown segment") {
      for
        _     <- seedCountry("PT", "Portugal")
        _     <- seedAirport("LIS", "LPPT", "Humberto Delgado", "Lisbon", "PT")
        _     <- seedAirport("OPO", "LPPR", "Sa Carneiro", "Porto", "PT")
        repo  <- ZIO.service[RouteRepository]
        found <- repo.findBySegment(IataCode("LIS"), IataCode("OPO"))
      yield assertTrue(found.isEmpty)
    },
    test("findAll includes saved routes") {
      for
        _    <- seedCountry("FR", "France")
        _    <- seedAirport("CDG", "LFPG", "Charles de Gaulle", "Paris", "FR")
        _    <- seedAirport("ORY", "LFPO", "Orly", "Paris", "FR")
        repo <- ZIO.service[RouteRepository]
        _    <- repo.save(Route(IataCode("CDG"), IataCode("ORY"), 15))
        all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
      yield assertTrue(all.exists(r => r.origin.value == "CDG" && r.destination.value == "ORY"))
    },
    test("findAllUnbounded includes saved routes without pagination") {
      for
        _    <- seedCountry("DE", "Germany")
        _    <- seedAirport("FRA", "EDDF", "Frankfurt am Main", "Frankfurt", "DE")
        _    <- seedAirport("MUC", "EDDM", "Franz Josef Strauss", "Munich", "DE")
        repo <- ZIO.service[RouteRepository]
        _    <- repo.save(Route(IataCode("FRA"), IataCode("MUC"), 300))
        all  <- repo.findAllUnbounded
      yield assertTrue(all.exists(r => r.origin.value == "FRA" && r.destination.value == "MUC"))
    },
    test("update changes the distance of an existing route") {
      for
        _      <- seedCountry("IT", "Italy")
        _      <- seedAirport("FCO", "LIRF", "Fiumicino", "Rome", "IT")
        _      <- seedAirport("MXP", "LIMC", "Malpensa", "Milan", "IT")
        repo   <- ZIO.service[RouteRepository]
        _      <- repo.save(Route(IataCode("FCO"), IataCode("MXP"), 500))
        updated = Route(IataCode("FCO"), IataCode("MXP"), 477)
        saved  <- repo.update(updated)
        found  <- repo.findBySegment(IataCode("FCO"), IataCode("MXP"))
      yield assertTrue(saved == updated, found.contains(updated))
    },
    test("update fails with RouteNotFound for an unknown segment") {
      for
        _     <- seedCountry("GR", "Greece")
        _     <- seedAirport("ATH", "LGAV", "Eleftherios Venizelos", "Athens", "GR")
        _     <- seedAirport("SKG", "LGTS", "Macedonia", "Thessaloniki", "GR")
        repo  <- ZIO.service[RouteRepository]
        error <- repo.update(Route(IataCode("ATH"), IataCode("SKG"), 400)).flip
      yield assertTrue(error == DomainError.RouteNotFound("ATH", "SKG"))
    },
    test("save fails with RouteAlreadyExists on a duplicate segment") {
      for
        _     <- seedCountry("NL", "Netherlands")
        _     <- seedAirport("AMS", "EHAM", "Schiphol", "Amsterdam", "NL")
        _     <- seedAirport("RTM", "EHRD", "Rotterdam The Hague", "Rotterdam", "NL")
        repo  <- ZIO.service[RouteRepository]
        _     <- repo.save(Route(IataCode("AMS"), IataCode("RTM"), 60))
        error <- repo.save(Route(IataCode("AMS"), IataCode("RTM"), 60)).flip
      yield assertTrue(error == DomainError.RouteAlreadyExists("AMS", "RTM"))
    },
    test("delete removes an existing route") {
      for
        _     <- seedCountry("CH", "Switzerland")
        _     <- seedAirport("ZRH", "LSZH", "Zurich", "Zurich", "CH")
        _     <- seedAirport("GVA", "LSGG", "Geneva", "Geneva", "CH")
        repo  <- ZIO.service[RouteRepository]
        _     <- repo.save(Route(IataCode("ZRH"), IataCode("GVA"), 210))
        _     <- repo.delete(IataCode("ZRH"), IataCode("GVA"))
        found <- repo.findBySegment(IataCode("ZRH"), IataCode("GVA"))
      yield assertTrue(found.isEmpty)
    },
    test("delete fails with RouteNotFound for an unknown segment") {
      for
        _     <- seedCountry("BE", "Belgium")
        _     <- seedAirport("BRU", "EBBR", "Brussels", "Brussels", "BE")
        _     <- seedAirport("LGG", "EBLG", "Liege", "Liege", "BE")
        repo  <- ZIO.service[RouteRepository]
        error <- repo.delete(IataCode("BRU"), IataCode("LGG")).flip
      yield assertTrue(error == DomainError.RouteNotFound("BRU", "LGG"))
    }
  )
