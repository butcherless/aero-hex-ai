package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineRepository, IcaoCode}
import dev.cmartin.aerohex.domain.country.{Country, CountryCode, CountryRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.ZIO
import zio.test.*

import java.time.LocalDate

// Behavior contract shared by QuillAirlineRepositoryItSpec and DoobieAirlineRepositoryItSpec — both
// adapters must satisfy the same AirlineRepository port and behave identically here, so the test
// bodies are shared; only the provided layer differs per adapter.
object AirlineRepositoryContractSpec:

  private def seedCountry(code: String, name: String): ZIO[CountryRepository, DomainError, Unit] =
    ZIO.serviceWithZIO[CountryRepository](_.save(Country(CountryCode.unsafeMake(code), name)).unit)

  def tests: List[Spec[AirlineRepository & CountryRepository, Any]] = List(
    test("saves and finds an airline by icao code") {
      for
        _      <- seedCountry("ES", "Spain")
        repo   <- ZIO.service[AirlineRepository]
        iberia  = Airline(IcaoCode("IBE"), "Iberia", LocalDate.of(1927, 6, 28))
        saved  <- repo.save(iberia, CountryCode("ES"))
        found  <- repo.findByIcao(IcaoCode("IBE"))
      yield assertTrue(saved == iberia, found.contains(iberia))
    },
    test("findAll includes saved airlines") {
      for
        _    <- seedCountry("FR", "France")
        repo <- ZIO.service[AirlineRepository]
        _    <- repo.save(Airline(IcaoCode("AFR"), "Air France", LocalDate.of(1933, 10, 7)), CountryCode("FR"))
        all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
      yield assertTrue(all.exists(_.icao.value == "AFR"))
    },
    test("update changes the name and foundation date of an existing airline") {
      for
        _       <- seedCountry("PT", "Portugal")
        repo    <- ZIO.service[AirlineRepository]
        _       <- repo.save(Airline(IcaoCode("TAP"), "TAP", LocalDate.of(1945, 3, 14)), CountryCode("PT"))
        updated  = Airline(IcaoCode("TAP"), "TAP Air Portugal", LocalDate.of(1945, 3, 14))
        saved   <- repo.update(updated, CountryCode("PT"))
        found   <- repo.findByIcao(IcaoCode("TAP"))
      yield assertTrue(saved == updated, found.contains(updated))
    },
    test("update fails with AirlineNotFound for an unknown icao code") {
      for
        _     <- seedCountry("LU", "Luxembourg")
        repo  <- ZIO.service[AirlineRepository]
        error <- repo.update(Airline(IcaoCode("ZZZ"), "Nowhere", LocalDate.of(2000, 1, 1)), CountryCode("LU")).flip
      yield assertTrue(error == DomainError.AirlineNotFound("ZZZ"))
    },
    test("update fails with CountryNotFound when the new country code does not exist") {
      for
        _     <- seedCountry("BE", "Belgium")
        repo  <- ZIO.service[AirlineRepository]
        _     <- repo.save(Airline(IcaoCode("BEL"), "Brussels Air", LocalDate.of(1990, 1, 1)), CountryCode("BE"))
        error <-
          repo.update(Airline(IcaoCode("BEL"), "Brussels Air", LocalDate.of(1990, 1, 1)), CountryCode("YY")).flip
      yield assertTrue(error == DomainError.CountryNotFound("YY"))
    },
    test("save fails with CountryNotFound for an unknown country code") {
      for
        repo  <- ZIO.service[AirlineRepository]
        error <- repo.save(Airline(IcaoCode("XXX"), "Nowhere", LocalDate.of(2000, 1, 1)), CountryCode("XX")).flip
      yield assertTrue(error == DomainError.CountryNotFound("XX"))
    },
    test("save fails with AirlineAlreadyExists on a duplicate icao code") {
      for
        _     <- seedCountry("NL", "Netherlands")
        repo  <- ZIO.service[AirlineRepository]
        _     <- repo.save(Airline(IcaoCode("KLM"), "KLM", LocalDate.of(1919, 10, 7)), CountryCode("NL"))
        error <- repo.save(Airline(IcaoCode("KLM"), "KLM", LocalDate.of(1919, 10, 7)), CountryCode("NL")).flip
      yield assertTrue(error == DomainError.AirlineAlreadyExists("KLM"))
    },
    test("delete removes an existing airline") {
      for
        _     <- seedCountry("CH", "Switzerland")
        repo  <- ZIO.service[AirlineRepository]
        _     <- repo.save(Airline(IcaoCode("SWR"), "Swiss", LocalDate.of(2002, 3, 31)), CountryCode("CH"))
        _     <- repo.delete(IcaoCode("SWR"))
        found <- repo.findByIcao(IcaoCode("SWR"))
      yield assertTrue(found.isEmpty)
    },
    test("delete fails with AirlineNotFound for an unknown icao code") {
      for
        repo  <- ZIO.service[AirlineRepository]
        error <- repo.delete(IcaoCode("ZZZ")).flip
      yield assertTrue(error == DomainError.AirlineNotFound("ZZZ"))
    }
  )
