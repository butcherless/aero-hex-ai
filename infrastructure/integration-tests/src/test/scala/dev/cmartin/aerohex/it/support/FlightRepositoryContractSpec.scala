package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.airline.{Airline, AirlineRepository, IcaoCode}
import dev.cmartin.aerohex.domain.airport.{Airport, AirportRepository, IataCode}
import dev.cmartin.aerohex.domain.country.{Country, CountryCode, CountryRepository}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{Flight, FlightCode, FlightRepository}
import dev.cmartin.aerohex.shared.Pagination
import java.time.{LocalDate, LocalTime}
import zio.ZIO
import zio.test.*

// Behavior contract shared by QuillFlightRepositoryItSpec and DoobieFlightRepositoryItSpec — both
// adapters must satisfy the same FlightRepository port and behave identically here, so the test
// bodies are shared; only the provided layer differs per adapter. Flight depends on two Airports
// (origin + destination) and an Airline, each of which itself depends on Country, so every test
// seeds the full chain first.
object FlightRepositoryContractSpec:

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
        Airport(IataCode.unsafeMake(iata), IcaoCode.unsafeMake(icao), name, city),
        CountryCode.unsafeMake(countryCode)
      ).unit
    )

  private def seedAirline(
      icao: String,
      name: String,
      countryCode: String
  ): ZIO[AirlineRepository, DomainError, Unit] =
    ZIO.serviceWithZIO[AirlineRepository](
      _.save(Airline(IcaoCode.unsafeMake(icao), name, LocalDate.of(2000, 1, 1)), CountryCode.unsafeMake(countryCode)).unit
    )

  def tests: List[Spec[FlightRepository & AirportRepository & AirlineRepository & CountryRepository, Any]] = List(
    test("saves and finds a flight by code") {
      for
        _     <- seedCountry("ES", "Spain")
        _     <- seedAirport("MAD", "LEMD", "Barajas", "Madrid", "ES")
        _     <- seedAirport("TFN", "GCXO", "Norte", "Tenerife", "ES")
        _     <- seedAirline("AEA", "Air Europa", "ES")
        repo  <- ZIO.service[FlightRepository]
        ux9117 = Flight(
                   FlightCode("UX9117"),
                   Some("AEA9117"),
                   LocalTime.of(7, 5),
                   LocalTime.of(8, 55),
                   IataCode("MAD"),
                   IataCode("TFN"),
                   IcaoCode("AEA")
                 )
        saved <- repo.save(ux9117)
        found <- repo.findByCode(FlightCode("UX9117"))
      yield assertTrue(saved == ux9117, found.contains(ux9117))
    },
    test("findAll includes saved flights") {
      for
        _    <- seedCountry("FR", "France")
        _    <- seedAirport("CDG", "LFPG", "Charles de Gaulle", "Paris", "FR")
        _    <- seedAirport("ORY", "LFPO", "Orly", "Paris", "FR")
        _    <- seedAirline("AFR", "Air France", "FR")
        repo <- ZIO.service[FlightRepository]
        _    <- repo.save(
                  Flight(
                    FlightCode("AF1234"),
                    None,
                    LocalTime.of(9, 0),
                    LocalTime.of(10, 0),
                    IataCode("CDG"),
                    IataCode("ORY"),
                    IcaoCode("AFR")
                  )
                )
        all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
      yield assertTrue(all.exists(_.code.value == "AF1234"))
    },
    test("update changes the schedule, alias, and airline of an existing flight") {
      for
        _       <- seedCountry("PT", "Portugal")
        _       <- seedAirport("LIS", "LPPT", "Humberto Delgado", "Lisbon", "PT")
        _       <- seedAirport("OPO", "LPPR", "Sa Carneiro", "Porto", "PT")
        _       <- seedAirline("TAP", "TAP", "PT")
        _       <- seedAirline("PGA", "Portugalia", "PT")
        repo    <- ZIO.service[FlightRepository]
        _       <- repo.save(
                     Flight(
                       FlightCode("TP1234"),
                       None,
                       LocalTime.of(9, 0),
                       LocalTime.of(9, 50),
                       IataCode("LIS"),
                       IataCode("OPO"),
                       IcaoCode("TAP")
                     )
                   )
        updated  = Flight(
                     FlightCode("TP1234"),
                     Some("PGA1234"),
                     LocalTime.of(9, 30),
                     LocalTime.of(10, 20),
                     IataCode("LIS"),
                     IataCode("OPO"),
                     IcaoCode("PGA")
                   )
        saved   <- repo.update(updated)
        found   <- repo.findByCode(FlightCode("TP1234"))
      yield assertTrue(saved == updated, found.contains(updated))
    },
    test("update fails with FlightNotFound for an unknown code") {
      for
        _     <- seedCountry("LU", "Luxembourg")
        _     <- seedAirport("LUX", "ELLX", "Findel", "Luxembourg", "LU")
        _     <- seedAirport("BRU", "EBBR", "Zaventem", "Brussels", "LU")
        _     <- seedAirline("LGL", "Luxair", "LU")
        repo  <- ZIO.service[FlightRepository]
        error <- repo
                   .update(
                     Flight(
                       FlightCode("ZZ0000"),
                       None,
                       LocalTime.of(9, 0),
                       LocalTime.of(10, 0),
                       IataCode("LUX"),
                       IataCode("BRU"),
                       IcaoCode("LGL")
                     )
                   )
                   .flip
      yield assertTrue(error == DomainError.FlightNotFound("ZZ0000"))
    },
    test("update fails with AirportNotFound when the new origin airport does not exist") {
      for
        _     <- seedCountry("BE", "Belgium")
        _     <- seedAirport("OST", "EBOS", "Ostend-Bruges", "Ostend", "BE")
        _     <- seedAirport("LGG", "EBLG", "Liege", "Liege", "BE")
        _     <- seedAirline("BEL", "Brussels Air", "BE")
        repo  <- ZIO.service[FlightRepository]
        _     <- repo.save(
                   Flight(
                     FlightCode("SN1234"),
                     None,
                     LocalTime.of(9, 0),
                     LocalTime.of(9, 40),
                     IataCode("OST"),
                     IataCode("LGG"),
                     IcaoCode("BEL")
                   )
                 )
        error <- repo
                   .update(
                     Flight(
                       FlightCode("SN1234"),
                       None,
                       LocalTime.of(9, 0),
                       LocalTime.of(9, 40),
                       IataCode("XXX"),
                       IataCode("LGG"),
                       IcaoCode("BEL")
                     )
                   )
                   .flip
      yield assertTrue(error == DomainError.AirportNotFound("XXX"))
    },
    test("update fails with AirlineNotFound when the new airline code does not exist") {
      for
        _     <- seedCountry("NL", "Netherlands")
        _     <- seedAirport("AMS", "EHAM", "Schiphol", "Amsterdam", "NL")
        _     <- seedAirport("RTM", "EHRD", "Rotterdam", "Rotterdam", "NL")
        _     <- seedAirline("KLM", "KLM", "NL")
        repo  <- ZIO.service[FlightRepository]
        _     <- repo.save(
                   Flight(
                     FlightCode("KL1234"),
                     None,
                     LocalTime.of(9, 0),
                     LocalTime.of(9, 30),
                     IataCode("AMS"),
                     IataCode("RTM"),
                     IcaoCode("KLM")
                   )
                 )
        error <- repo
                   .update(
                     Flight(
                       FlightCode("KL1234"),
                       None,
                       LocalTime.of(9, 0),
                       LocalTime.of(9, 30),
                       IataCode("AMS"),
                       IataCode("RTM"),
                       IcaoCode("YYY")
                     )
                   )
                   .flip
      yield assertTrue(error == DomainError.AirlineNotFound("YYY"))
    },
    test("save fails with AirportNotFound for an unknown origin airport") {
      for
        _     <- seedCountry("CH", "Switzerland")
        _     <- seedAirport("ZRH", "LSZH", "Zurich", "Zurich", "CH")
        _     <- seedAirline("SWR", "Swiss", "CH")
        repo  <- ZIO.service[FlightRepository]
        error <- repo
                   .save(
                     Flight(
                       FlightCode("LX0000"),
                       None,
                       LocalTime.of(9, 0),
                       LocalTime.of(10, 0),
                       IataCode("XXX"),
                       IataCode("ZRH"),
                       IcaoCode("SWR")
                     )
                   )
                   .flip
      yield assertTrue(error == DomainError.AirportNotFound("XXX"))
    },
    test("save fails with AirlineNotFound for an unknown airline code") {
      for
        _     <- seedCountry("SE", "Sweden")
        _     <- seedAirport("ARN", "ESSA", "Arlanda", "Stockholm", "SE")
        _     <- seedAirport("GOT", "ESGG", "Landvetter", "Gothenburg", "SE")
        repo  <- ZIO.service[FlightRepository]
        error <- repo
                   .save(
                     Flight(
                       FlightCode("SK0000"),
                       None,
                       LocalTime.of(9, 0),
                       LocalTime.of(10, 0),
                       IataCode("ARN"),
                       IataCode("GOT"),
                       IcaoCode("YYY")
                     )
                   )
                   .flip
      yield assertTrue(error == DomainError.AirlineNotFound("YYY"))
    },
    test("save fails with FlightAlreadyExists on a duplicate code") {
      for
        _     <- seedCountry("NO", "Norway")
        _     <- seedAirport("OSL", "ENGM", "Gardermoen", "Oslo", "NO")
        _     <- seedAirport("BGO", "ENBR", "Flesland", "Bergen", "NO")
        _     <- seedAirline("SAS", "SAS", "NO")
        repo  <- ZIO.service[FlightRepository]
        flight = Flight(
                   FlightCode("SK4567"),
                   None,
                   LocalTime.of(9, 0),
                   LocalTime.of(10, 0),
                   IataCode("OSL"),
                   IataCode("BGO"),
                   IcaoCode("SAS")
                 )
        _     <- repo.save(flight)
        error <- repo.save(flight).flip
      yield assertTrue(error == DomainError.FlightAlreadyExists("SK4567"))
    },
    test("delete removes an existing flight") {
      for
        _     <- seedCountry("DK", "Denmark")
        _     <- seedAirport("CPH", "EKCH", "Kastrup", "Copenhagen", "DK")
        _     <- seedAirport("AAL", "EKYT", "Aalborg", "Aalborg", "DK")
        _     <- seedAirline("DAN", "DAT", "DK")
        repo  <- ZIO.service[FlightRepository]
        _     <- repo.save(
                   Flight(
                     FlightCode("DX1234"),
                     None,
                     LocalTime.of(9, 0),
                     LocalTime.of(9, 45),
                     IataCode("CPH"),
                     IataCode("AAL"),
                     IcaoCode("DAN")
                   )
                 )
        _     <- repo.delete(FlightCode("DX1234"))
        found <- repo.findByCode(FlightCode("DX1234"))
      yield assertTrue(found.isEmpty)
    },
    test("delete fails with FlightNotFound for an unknown code") {
      for
        repo  <- ZIO.service[FlightRepository]
        error <- repo.delete(FlightCode("ZZ0000")).flip
      yield assertTrue(error == DomainError.FlightNotFound("ZZ0000"))
    }
  )
