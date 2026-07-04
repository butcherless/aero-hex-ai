package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, Country, CountryCode, IataCode}
import dev.cmartin.aerohex.infrastructure.persistence.quill.repository.{QuillAirportRepository, QuillCountryRepository}
import dev.cmartin.aerohex.it.support.PostgresContainerSupport
import dev.cmartin.aerohex.shared.Pagination
import zio.*
import zio.test.*

import javax.sql.DataSource

object QuillAirportRepositoryItSpec extends ZIOSpecDefault {

  private def seedCountry(ds: DataSource, code: String, name: String): IO[DomainError, Unit] =
    new QuillCountryRepository(ds).save(Country(CountryCode(code), name)).unit

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillAirportRepository")(
      test("saves and finds an airport by iata code") {
        for
          ds     <- ZIO.service[DataSource]
          repo    = new QuillAirportRepository(ds)
          _      <- seedCountry(ds, "ES", "Spain")
          madrid  = Airport(IataCode("MAD"), "LEMD", "Adolfo Suarez Madrid-Barajas", "Madrid", CountryCode("ES"))
          saved  <- repo.save(madrid)
          found  <- repo.findByIata(IataCode("MAD"))
        yield assertTrue(saved == madrid, found.contains(madrid))
      },
      test("findAll includes saved airports") {
        for
          ds   <- ZIO.service[DataSource]
          repo  = new QuillAirportRepository(ds)
          _    <- seedCountry(ds, "FR", "France")
          _    <- repo.save(Airport(IataCode("CDG"), "LFPG", "Charles de Gaulle", "Paris", CountryCode("FR")))
          all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
        yield assertTrue(all.exists(_.iataCode.value == "CDG"))
      },
      test("searchByName matches a case-insensitive substring") {
        for
          ds      <- ZIO.service[DataSource]
          repo     = new QuillAirportRepository(ds)
          _       <- seedCountry(ds, "IT", "Italy")
          _       <- repo.save(Airport(IataCode("FCO"), "LIRF", "Leonardo da Vinci-Fiumicino", "Rome", CountryCode("IT")))
          results <- repo.searchByName("fiumicino")
        yield assertTrue(results.exists(_.iataCode.value == "FCO"))
      },
      test("findByCountry returns airports in that country") {
        for
          ds   <- ZIO.service[DataSource]
          repo  = new QuillAirportRepository(ds)
          _    <- seedCountry(ds, "DE", "Germany")
          _    <- repo.save(Airport(IataCode("FRA"), "EDDF", "Frankfurt am Main", "Frankfurt", CountryCode("DE")))
          list <- repo.findByCountry(CountryCode("DE"), Pagination(page = 1, pageSize = 100))
        yield assertTrue(list.exists(_.iataCode.value == "FRA"))
      },
      test("update changes the name and city of an existing airport") {
        for
          ds      <- ZIO.service[DataSource]
          repo     = new QuillAirportRepository(ds)
          _       <- seedCountry(ds, "PT", "Portugal")
          _       <- repo.save(Airport(IataCode("LIS"), "LPPT", "Lisbon Portela", "Lisbon", CountryCode("PT")))
          updated  = Airport(IataCode("LIS"), "LPPT", "Humberto Delgado", "Lisboa", CountryCode("PT"))
          saved   <- repo.update(updated)
          found   <- repo.findByIata(IataCode("LIS"))
        yield assertTrue(saved == updated, found.contains(updated))
      },
      test("update fails with AirportNotFound for an unknown iata code") {
        for
          ds    <- ZIO.service[DataSource]
          repo   = new QuillAirportRepository(ds)
          _     <- seedCountry(ds, "LU", "Luxembourg")
          error <- repo.update(Airport(IataCode("ZZZ"), "ZZZZ", "Nowhere", "Nowhere", CountryCode("LU"))).flip
        yield assertTrue(error == DomainError.AirportNotFound("ZZZ"))
      },
      test("update fails with CountryNotFound when the new country code does not exist") {
        for
          ds    <- ZIO.service[DataSource]
          repo   = new QuillAirportRepository(ds)
          _     <- seedCountry(ds, "BE", "Belgium")
          _     <- repo.save(Airport(IataCode("BRU"), "EBBR", "Brussels", "Brussels", CountryCode("BE")))
          error <- repo.update(Airport(IataCode("BRU"), "EBBR", "Brussels", "Brussels", CountryCode("YY"))).flip
        yield assertTrue(error == DomainError.CountryNotFound("YY"))
      },
      test("save fails with CountryNotFound for an unknown country code") {
        for
          ds    <- ZIO.service[DataSource]
          repo   = new QuillAirportRepository(ds)
          error <- repo.save(Airport(IataCode("XXX"), "XXXX", "Nowhere", "Nowhere", CountryCode("XX"))).flip
        yield assertTrue(error == DomainError.CountryNotFound("XX"))
      },
      test("save fails with AirportAlreadyExists on a duplicate iata code") {
        for
          ds    <- ZIO.service[DataSource]
          repo   = new QuillAirportRepository(ds)
          _     <- seedCountry(ds, "NL", "Netherlands")
          _     <- repo.save(Airport(IataCode("AMS"), "EHAM", "Schiphol", "Amsterdam", CountryCode("NL")))
          error <- repo.save(Airport(IataCode("AMS"), "EHAM", "Schiphol", "Amsterdam", CountryCode("NL"))).flip
        yield assertTrue(error == DomainError.AirportAlreadyExists("AMS"))
      },
      test("delete removes an existing airport") {
        for
          ds    <- ZIO.service[DataSource]
          repo   = new QuillAirportRepository(ds)
          _     <- seedCountry(ds, "CH", "Switzerland")
          _     <- repo.save(Airport(IataCode("ZRH"), "LSZH", "Zurich", "Zurich", CountryCode("CH")))
          _     <- repo.delete(IataCode("ZRH"))
          found <- repo.findByIata(IataCode("ZRH"))
        yield assertTrue(found.isEmpty)
      }
    ).provideLayerShared(PostgresContainerSupport.dataSourceLayer)
}
