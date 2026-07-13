package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.aircraft.{Aircraft, AircraftRepository, Registration}
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineRepository, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.country.{Country, CountryCode, CountryRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.ZIO
import zio.test.*

import java.time.LocalDate

// Behavior contract shared by QuillAircraftRepositoryItSpec and DoobieAircraftRepositoryItSpec — both
// adapters must satisfy the same AircraftRepository port and behave identically here, so the test
// bodies are shared; only the provided layer differs per adapter. Aircraft depends on Airline, which
// itself depends on Country, so every test seeds both parents first.
object AircraftRepositoryContractSpec:

  private def seedCountry(code: String, name: String): ZIO[CountryRepository, DomainError, Unit] =
    ZIO.serviceWithZIO[CountryRepository](_.save(Country(CountryCode.unsafeMake(code), name)).unit)

  private def seedAirline(icao: String, name: String, countryCode: String): ZIO[AirlineRepository, DomainError, Unit] =
    ZIO.serviceWithZIO[AirlineRepository](
      _.save(Airline(AirlineIcaoCode.unsafeMake(icao), name, LocalDate.of(2000, 1, 1)), CountryCode.unsafeMake(countryCode)).unit
    )

  def tests: List[Spec[AircraftRepository & AirlineRepository & CountryRepository, Any]] = List(
    test("saves and finds an aircraft by registration") {
      for
        _      <- seedCountry("ES", "Spain")
        _      <- seedAirline("IBE", "Iberia", "ES")
        repo   <- ZIO.service[AircraftRepository]
        ecMig   = Aircraft(Registration("EC-MIG"), "B788", "Boeing 787-8", AirlineIcaoCode("IBE"))
        saved  <- repo.save(ecMig)
        found  <- repo.findByRegistration(Registration("EC-MIG"))
      yield assertTrue(saved == ecMig, found.contains(ecMig))
    },
    test("findAll includes saved aircraft") {
      for
        _    <- seedCountry("FR", "France")
        _    <- seedAirline("AFR", "Air France", "FR")
        repo <- ZIO.service[AircraftRepository]
        _    <- repo.save(Aircraft(Registration("F-GKXA"), "A388", "Airbus A380-800", AirlineIcaoCode("AFR")))
        all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
      yield assertTrue(all.exists(_.registration.value == "F-GKXA"))
    },
    test("update changes the type code, description, and airline of an existing aircraft") {
      for
        _       <- seedCountry("PT", "Portugal")
        _       <- seedAirline("TAP", "TAP", "PT")
        _       <- seedAirline("PGA", "Portugalia", "PT")
        repo    <- ZIO.service[AircraftRepository]
        _       <- repo.save(Aircraft(Registration("CS-TUA"), "A319", "Airbus A319", AirlineIcaoCode("TAP")))
        updated  = Aircraft(Registration("CS-TUA"), "A320", "Airbus A320", AirlineIcaoCode("PGA"))
        saved   <- repo.update(updated)
        found   <- repo.findByRegistration(Registration("CS-TUA"))
      yield assertTrue(saved == updated, found.contains(updated))
    },
    test("update fails with AircraftNotFound for an unknown registration") {
      for
        _     <- seedCountry("LU", "Luxembourg")
        _     <- seedAirline("LGL", "Luxair", "LU")
        repo  <- ZIO.service[AircraftRepository]
        error <- repo.update(Aircraft(Registration("ZZ-ZZZ"), "A320", "Nowhere", AirlineIcaoCode("LGL"))).flip
      yield assertTrue(error == DomainError.AircraftNotFound("ZZ-ZZZ"))
    },
    test("update fails with AirlineNotFound when the new airline code does not exist") {
      for
        _     <- seedCountry("BE", "Belgium")
        _     <- seedAirline("BEL", "Brussels Air", "BE")
        repo  <- ZIO.service[AircraftRepository]
        _     <- repo.save(Aircraft(Registration("OO-ABC"), "A320", "Airbus A320", AirlineIcaoCode("BEL")))
        error <- repo.update(Aircraft(Registration("OO-ABC"), "A320", "Airbus A320", AirlineIcaoCode("YYY"))).flip
      yield assertTrue(error == DomainError.AirlineNotFound("YYY"))
    },
    test("save fails with AirlineNotFound for an unknown airline code") {
      for
        repo  <- ZIO.service[AircraftRepository]
        error <- repo.save(Aircraft(Registration("N00000"), "B737", "Boeing 737", AirlineIcaoCode("XXX"))).flip
      yield assertTrue(error == DomainError.AirlineNotFound("XXX"))
    },
    test("save fails with AircraftAlreadyExists on a duplicate registration") {
      for
        _     <- seedCountry("NL", "Netherlands")
        _     <- seedAirline("KLM", "KLM", "NL")
        repo  <- ZIO.service[AircraftRepository]
        _     <- repo.save(Aircraft(Registration("PH-BHA"), "B789", "Boeing 787-9", AirlineIcaoCode("KLM")))
        error <- repo.save(Aircraft(Registration("PH-BHA"), "B789", "Boeing 787-9", AirlineIcaoCode("KLM"))).flip
      yield assertTrue(error == DomainError.AircraftAlreadyExists("PH-BHA"))
    },
    test("delete removes an existing aircraft") {
      for
        _     <- seedCountry("CH", "Switzerland")
        _     <- seedAirline("SWR", "Swiss", "CH")
        repo  <- ZIO.service[AircraftRepository]
        _     <- repo.save(Aircraft(Registration("HB-JNA"), "A343", "Airbus A340-300", AirlineIcaoCode("SWR")))
        _     <- repo.delete(Registration("HB-JNA"))
        found <- repo.findByRegistration(Registration("HB-JNA"))
      yield assertTrue(found.isEmpty)
    },
    test("delete fails with AircraftNotFound for an unknown registration") {
      for
        repo  <- ZIO.service[AircraftRepository]
        error <- repo.delete(Registration("ZZ-ZZZ")).flip
      yield assertTrue(error == DomainError.AircraftNotFound("ZZ-ZZZ"))
    }
  )
