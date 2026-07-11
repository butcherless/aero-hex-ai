package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, Country, CountryCode, IataCode, IcaoCode}
import dev.cmartin.aerohex.domain.port.out.{AirportRepository, CountryRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.ZIO
import zio.test.*

// Behavior contract shared by QuillAirportRepositoryItSpec and DoobieAirportRepositoryItSpec —
// both adapters must satisfy the same AirportRepository port and behave identically here (unlike
// CountryRepositoryContractSpec, Doobie's Airport.save also fails on a duplicate iata code), so the
// test bodies are shared; only the provided layer differs per adapter.
object AirportRepositoryContractSpec:

  private def seedCountry(code: String, name: String): ZIO[CountryRepository, DomainError, Unit] =
    ZIO.serviceWithZIO[CountryRepository](_.save(Country(CountryCode(code), name)).unit)

  def tests: List[Spec[AirportRepository & CountryRepository, Any]] = List(
    test("saves and finds an airport by iata code") {
      for
        _      <- seedCountry("ES", "Spain")
        repo   <- ZIO.service[AirportRepository]
        madrid  = Airport(IataCode("MAD"), IcaoCode("LEMD"), "Adolfo Suarez Madrid-Barajas", "Madrid")
        saved  <- repo.save(madrid, CountryCode("ES"))
        found  <- repo.findByIata(IataCode("MAD"))
      yield assertTrue(saved == madrid, found.contains(madrid))
    },
    test("findAll includes saved airports") {
      for
        _    <- seedCountry("FR", "France")
        repo <- ZIO.service[AirportRepository]
        _    <- repo.save(Airport(IataCode("CDG"), IcaoCode("LFPG"), "Charles de Gaulle", "Paris"), CountryCode("FR"))
        all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
      yield assertTrue(all.exists(_.iataCode.value == "CDG"))
    },
    test("searchByName matches a case-insensitive substring") {
      for
        _       <- seedCountry("IT", "Italy")
        repo    <- ZIO.service[AirportRepository]
        _       <-
          repo.save(Airport(IataCode("FCO"), IcaoCode("LIRF"), "Leonardo da Vinci-Fiumicino", "Rome"), CountryCode("IT"))
        results <- repo.searchByName("fiumicino")
      yield assertTrue(results.exists(_.iataCode.value == "FCO"))
    },
    test("findByCountry returns airports in that country") {
      for
        _    <- seedCountry("DE", "Germany")
        repo <- ZIO.service[AirportRepository]
        _    <- repo.save(Airport(IataCode("FRA"), IcaoCode("EDDF"), "Frankfurt am Main", "Frankfurt"), CountryCode("DE"))
        list <- repo.findByCountry(CountryCode("DE"), Pagination(page = 1, pageSize = 100))
      yield assertTrue(list.exists(_.iataCode.value == "FRA"))
    },
    test("update changes the name and city of an existing airport") {
      for
        _       <- seedCountry("PT", "Portugal")
        repo    <- ZIO.service[AirportRepository]
        _       <- repo.save(Airport(IataCode("LIS"), IcaoCode("LPPT"), "Lisbon Portela", "Lisbon"), CountryCode("PT"))
        updated  = Airport(IataCode("LIS"), IcaoCode("LPPT"), "Humberto Delgado", "Lisboa")
        saved   <- repo.update(updated, CountryCode("PT"))
        found   <- repo.findByIata(IataCode("LIS"))
      yield assertTrue(saved == updated, found.contains(updated))
    },
    test("update fails with AirportNotFound for an unknown iata code") {
      for
        _     <- seedCountry("LU", "Luxembourg")
        repo  <- ZIO.service[AirportRepository]
        error <- repo.update(Airport(IataCode("ZZZ"), IcaoCode("ZZZZ"), "Nowhere", "Nowhere"), CountryCode("LU")).flip
      yield assertTrue(error == DomainError.AirportNotFound("ZZZ"))
    },
    test("update fails with CountryNotFound when the new country code does not exist") {
      for
        _     <- seedCountry("BE", "Belgium")
        repo  <- ZIO.service[AirportRepository]
        _     <- repo.save(Airport(IataCode("BRU"), IcaoCode("EBBR"), "Brussels", "Brussels"), CountryCode("BE"))
        error <- repo.update(Airport(IataCode("BRU"), IcaoCode("EBBR"), "Brussels", "Brussels"), CountryCode("YY")).flip
      yield assertTrue(error == DomainError.CountryNotFound("YY"))
    },
    test("save fails with CountryNotFound for an unknown country code") {
      for
        repo  <- ZIO.service[AirportRepository]
        error <- repo.save(Airport(IataCode("XXX"), IcaoCode("XXXX"), "Nowhere", "Nowhere"), CountryCode("XX")).flip
      yield assertTrue(error == DomainError.CountryNotFound("XX"))
    },
    test("save fails with AirportAlreadyExists on a duplicate iata code") {
      for
        _     <- seedCountry("NL", "Netherlands")
        repo  <- ZIO.service[AirportRepository]
        _     <- repo.save(Airport(IataCode("AMS"), IcaoCode("EHAM"), "Schiphol", "Amsterdam"), CountryCode("NL"))
        error <- repo.save(Airport(IataCode("AMS"), IcaoCode("EHAM"), "Schiphol", "Amsterdam"), CountryCode("NL")).flip
      yield assertTrue(error == DomainError.AirportAlreadyExists("AMS"))
    },
    test("delete removes an existing airport") {
      for
        _     <- seedCountry("CH", "Switzerland")
        repo  <- ZIO.service[AirportRepository]
        _     <- repo.save(Airport(IataCode("ZRH"), IcaoCode("LSZH"), "Zurich", "Zurich"), CountryCode("CH"))
        _     <- repo.delete(IataCode("ZRH"))
        found <- repo.findByIata(IataCode("ZRH"))
      yield assertTrue(found.isEmpty)
    },
    test("delete fails with AirportNotFound for an unknown iata code") {
      for
        repo  <- ZIO.service[AirportRepository]
        error <- repo.delete(IataCode("ZZZ")).flip
      yield assertTrue(error == DomainError.AirportNotFound("ZZZ"))
    }
  )
