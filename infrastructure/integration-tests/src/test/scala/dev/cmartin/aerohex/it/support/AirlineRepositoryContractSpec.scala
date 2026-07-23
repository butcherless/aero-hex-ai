package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode, AirlineRepository}
import dev.cmartin.aerohex.domain.country.{Country, CountryCode, CountryRepository}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.ZIO
import zio.test.*

// Behavior contract shared by QuillAirlineRepositoryItSpec and DoobieAirlineRepositoryItSpec — both
// adapters must satisfy the same AirlineRepository port and behave identically here, so the test
// bodies are shared; only the provided layer differs per adapter.
object AirlineRepositoryContractSpec:

  private def seedCountry(code: String, name: String): ZIO[CountryRepository, DomainError, Unit] =
    ZIO.serviceWithZIO[CountryRepository](_.save(Country(CountryCode.unsafeMake(code), name)).unit)

  def tests: List[Spec[AirlineRepository & CountryRepository, Any]] = List(
    test("saves and finds an airline by icao code") {
      for
        _     <- seedCountry("ES", "Spain")
        repo  <- ZIO.service[AirlineRepository]
        iberia = Airline(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA"))
        saved <- repo.save(iberia, CountryCode("ES"))
        found <- repo.findByIcao(AirlineIcaoCode("IBE"))
      yield assertTrue(saved == iberia, found.contains(iberia))
    },
    test("findAll includes saved airlines") {
      for
        _    <- seedCountry("FR", "France")
        repo <- ZIO.service[AirlineRepository]
        _    <- repo.save(Airline(AirlineIcaoCode("AFR"), "Air France", None, Some("AIRFRANS")), CountryCode("FR"))
        all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
      yield assertTrue(all.exists(_.icao.value == "AFR"))
    },
    test("findByCountry returns airlines in that country") {
      for
        _    <- seedCountry("DE", "Germany")
        repo <- ZIO.service[AirlineRepository]
        _    <- repo.save(Airline(AirlineIcaoCode("DLH"), "Lufthansa", None, Some("LUFTHANSA")), CountryCode("DE"))
        list <- repo.findByCountry(CountryCode("DE"), Pagination(page = 1, pageSize = 100))
      yield assertTrue(list.exists(_.icao.value == "DLH"))
    },
    test("update changes the name and callsign of an existing airline") {
      for
        _      <- seedCountry("PT", "Portugal")
        repo   <- ZIO.service[AirlineRepository]
        _      <- repo.save(Airline(AirlineIcaoCode("TAP"), "TAP", None, Some("AIR PORTUGAL")), CountryCode("PT"))
        updated = Airline(AirlineIcaoCode("TAP"), "TAP Air Portugal", Some("TAP"), Some("AIR PORTUGAL"))
        saved  <- repo.update(updated, CountryCode("PT"))
        found  <- repo.findByIcao(AirlineIcaoCode("TAP"))
      yield assertTrue(saved == updated, found.contains(updated))
    },
    test("update fails with AirlineNotFound for an unknown icao code") {
      for
        _     <- seedCountry("LU", "Luxembourg")
        repo  <- ZIO.service[AirlineRepository]
        error <- repo.update(Airline(AirlineIcaoCode("ZZZ"), "Nowhere", None, None), CountryCode("LU")).flip
      yield assertTrue(error == DomainError.AirlineNotFound("ZZZ"))
    },
    test("update fails with CountryNotFound when the new country code does not exist") {
      for
        _     <- seedCountry("BE", "Belgium")
        repo  <- ZIO.service[AirlineRepository]
        _     <- repo.save(Airline(AirlineIcaoCode("BEL"), "Brussels Air", None, None), CountryCode("BE"))
        error <-
          repo.update(Airline(AirlineIcaoCode("BEL"), "Brussels Air", None, None), CountryCode("YY")).flip
      yield assertTrue(error == DomainError.CountryNotFound("YY"))
    },
    test("save fails with CountryNotFound for an unknown country code") {
      for
        repo  <- ZIO.service[AirlineRepository]
        error <- repo.save(Airline(AirlineIcaoCode("XXX"), "Nowhere", None, None), CountryCode("XX")).flip
      yield assertTrue(error == DomainError.CountryNotFound("XX"))
    },
    test("save fails with AirlineAlreadyExists on a duplicate icao code") {
      for
        _     <- seedCountry("NL", "Netherlands")
        repo  <- ZIO.service[AirlineRepository]
        _     <- repo.save(Airline(AirlineIcaoCode("KLM"), "KLM", None, Some("KLM")), CountryCode("NL"))
        error <- repo.save(Airline(AirlineIcaoCode("KLM"), "KLM", None, Some("KLM")), CountryCode("NL")).flip
      yield assertTrue(error == DomainError.AirlineAlreadyExists("KLM"))
    },
    test("delete removes an existing airline") {
      for
        _     <- seedCountry("CH", "Switzerland")
        repo  <- ZIO.service[AirlineRepository]
        _     <- repo.save(Airline(AirlineIcaoCode("SWR"), "Swiss", None, Some("SWISS")), CountryCode("CH"))
        _     <- repo.delete(AirlineIcaoCode("SWR"))
        found <- repo.findByIcao(AirlineIcaoCode("SWR"))
      yield assertTrue(found.isEmpty)
    },
    test("delete fails with AirlineNotFound for an unknown icao code") {
      for
        repo  <- ZIO.service[AirlineRepository]
        error <- repo.delete(AirlineIcaoCode("ZZZ")).flip
      yield assertTrue(error == DomainError.AirlineNotFound("ZZZ"))
    }
  )
