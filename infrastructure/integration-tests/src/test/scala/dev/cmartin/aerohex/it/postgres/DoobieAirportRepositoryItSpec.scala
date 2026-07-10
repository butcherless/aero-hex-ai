package dev.cmartin.aerohex.it.postgres

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, Country, CountryCode, IataCode, IcaoCode}
import dev.cmartin.aerohex.infrastructure.persistence.postgres.repository.{DoobieAirportRepository, DoobieCountryRepository}
import dev.cmartin.aerohex.it.support.PostgresContainerSupport
import dev.cmartin.aerohex.shared.Pagination
import doobie.util.transactor.Transactor
import zio.*
import zio.test.*

object DoobieAirportRepositoryItSpec extends ZIOSpecDefault {

  private def seedCountry(xa: Transactor[Task], code: String, name: String): IO[DomainError, Unit] =
    new DoobieCountryRepository(xa).save(Country(CountryCode(code), name)).unit

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DoobieAirportRepository")(
      test("saves and finds an airport by iata code") {
        for
          xa     <- ZIO.service[Transactor[Task]]
          repo    = new DoobieAirportRepository(xa)
          _      <- seedCountry(xa, "ES", "Spain")
          madrid  = Airport(IataCode("MAD"), IcaoCode("LEMD"), "Adolfo Suarez Madrid-Barajas", "Madrid")
          saved  <- repo.save(madrid, CountryCode("ES"))
          found  <- repo.findByIata(IataCode("MAD"))
        yield assertTrue(saved == madrid, found.contains(madrid))
      },
      test("findAll includes saved airports") {
        for
          xa   <- ZIO.service[Transactor[Task]]
          repo  = new DoobieAirportRepository(xa)
          _    <- seedCountry(xa, "FR", "France")
          _    <- repo.save(Airport(IataCode("CDG"), IcaoCode("LFPG"), "Charles de Gaulle", "Paris"), CountryCode("FR"))
          all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
        yield assertTrue(all.exists(_.iataCode.value == "CDG"))
      },
      test("searchByName matches a case-insensitive substring") {
        for
          xa      <- ZIO.service[Transactor[Task]]
          repo     = new DoobieAirportRepository(xa)
          _       <- seedCountry(xa, "IT", "Italy")
          _       <-
            repo.save(Airport(IataCode("FCO"), IcaoCode("LIRF"), "Leonardo da Vinci-Fiumicino", "Rome"), CountryCode("IT"))
          results <- repo.searchByName("fiumicino")
        yield assertTrue(results.exists(_.iataCode.value == "FCO"))
      },
      test("findByCountry returns airports in that country") {
        for
          xa   <- ZIO.service[Transactor[Task]]
          repo  = new DoobieAirportRepository(xa)
          _    <- seedCountry(xa, "DE", "Germany")
          _    <- repo.save(Airport(IataCode("FRA"), IcaoCode("EDDF"), "Frankfurt am Main", "Frankfurt"), CountryCode("DE"))
          list <- repo.findByCountry(CountryCode("DE"), Pagination(page = 1, pageSize = 100))
        yield assertTrue(list.exists(_.iataCode.value == "FRA"))
      },
      test("update changes the name and city of an existing airport") {
        for
          xa      <- ZIO.service[Transactor[Task]]
          repo     = new DoobieAirportRepository(xa)
          _       <- seedCountry(xa, "PT", "Portugal")
          _       <- repo.save(Airport(IataCode("LIS"), IcaoCode("LPPT"), "Lisbon Portela", "Lisbon"), CountryCode("PT"))
          updated  = Airport(IataCode("LIS"), IcaoCode("LPPT"), "Humberto Delgado", "Lisboa")
          saved   <- repo.update(updated, CountryCode("PT"))
          found   <- repo.findByIata(IataCode("LIS"))
        yield assertTrue(saved == updated, found.contains(updated))
      },
      test("update fails with AirportNotFound for an unknown iata code") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieAirportRepository(xa)
          _     <- seedCountry(xa, "LU", "Luxembourg")
          error <- repo.update(Airport(IataCode("ZZZ"), IcaoCode("ZZZZ"), "Nowhere", "Nowhere"), CountryCode("LU")).flip
        yield assertTrue(error == DomainError.AirportNotFound("ZZZ"))
      },
      test("update fails with CountryNotFound when the new country code does not exist") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieAirportRepository(xa)
          _     <- seedCountry(xa, "BE", "Belgium")
          _     <- repo.save(Airport(IataCode("BRU"), IcaoCode("EBBR"), "Brussels", "Brussels"), CountryCode("BE"))
          error <- repo.update(Airport(IataCode("BRU"), IcaoCode("EBBR"), "Brussels", "Brussels"), CountryCode("YY")).flip
        yield assertTrue(error == DomainError.CountryNotFound("YY"))
      },
      test("save fails with CountryNotFound for an unknown country code") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieAirportRepository(xa)
          error <- repo.save(Airport(IataCode("XXX"), IcaoCode("XXXX"), "Nowhere", "Nowhere"), CountryCode("XX")).flip
        yield assertTrue(error == DomainError.CountryNotFound("XX"))
      },
      test("save fails with AirportAlreadyExists on a duplicate iata code") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieAirportRepository(xa)
          _     <- seedCountry(xa, "NL", "Netherlands")
          _     <- repo.save(Airport(IataCode("AMS"), IcaoCode("EHAM"), "Schiphol", "Amsterdam"), CountryCode("NL"))
          error <- repo.save(Airport(IataCode("AMS"), IcaoCode("EHAM"), "Schiphol", "Amsterdam"), CountryCode("NL")).flip
        yield assertTrue(error == DomainError.AirportAlreadyExists("AMS"))
      },
      test("delete removes an existing airport") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieAirportRepository(xa)
          _     <- seedCountry(xa, "CH", "Switzerland")
          _     <- repo.save(Airport(IataCode("ZRH"), IcaoCode("LSZH"), "Zurich", "Zurich"), CountryCode("CH"))
          _     <- repo.delete(IataCode("ZRH"))
          found <- repo.findByIata(IataCode("ZRH"))
        yield assertTrue(found.isEmpty)
      }
    ).provideLayerShared(PostgresContainerSupport.transactorLayer)
}
