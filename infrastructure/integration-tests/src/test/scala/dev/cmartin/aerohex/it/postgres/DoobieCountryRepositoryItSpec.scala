package dev.cmartin.aerohex.it.postgres

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.infrastructure.persistence.postgres.repository.DoobieCountryRepository
import dev.cmartin.aerohex.it.support.PostgresContainerSupport
import dev.cmartin.aerohex.shared.Pagination
import doobie.util.transactor.Transactor
import zio.*
import zio.test.*

object DoobieCountryRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DoobieCountryRepository")(
      test("saves and finds a country by code") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieCountryRepository(xa)
          spain  = Country(CountryCode("ES"), "Spain")
          saved <- repo.save(spain)
          found <- repo.findByCode(CountryCode("ES"))
        yield assertTrue(saved == spain, found.contains(spain))
      },
      test("findAll includes saved countries") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieCountryRepository(xa)
          _     <- repo.save(Country(CountryCode("DE"), "Germany"))
          all   <- repo.findAll(Pagination(page = 1, pageSize = 100))
        yield assertTrue(all.exists(_.code.value == "DE"))
      },
      test("searchByName matches a case-insensitive substring") {
        for
          xa      <- ZIO.service[Transactor[Task]]
          repo     = new DoobieCountryRepository(xa)
          _       <- repo.save(Country(CountryCode("IT"), "Italy"))
          results <- repo.searchByName("ital")
        yield assertTrue(results.exists(_.code.value == "IT"))
      },
      test("update changes the name of an existing country") {
        for
          xa      <- ZIO.service[Transactor[Task]]
          repo     = new DoobieCountryRepository(xa)
          _       <- repo.save(Country(CountryCode("PT"), "Portugal"))
          updated <- repo.update(Country(CountryCode("PT"), "Republica Portuguesa"))
          found   <- repo.findByCode(CountryCode("PT"))
        yield assertTrue(updated.name == "Republica Portuguesa", found.map(_.name).contains("Republica Portuguesa"))
      },
      test("update fails with CountryNotFound for an unknown code") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieCountryRepository(xa)
          error <- repo.update(Country(CountryCode("ZZ"), "Nowhere")).flip
        yield assertTrue(error == DomainError.CountryNotFound("ZZ"))
      },
      test("delete removes an existing country") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieCountryRepository(xa)
          _     <- repo.save(Country(CountryCode("NL"), "Netherlands"))
          _     <- repo.delete(CountryCode("NL"))
          found <- repo.findByCode(CountryCode("NL"))
        yield assertTrue(found.isEmpty)
      },
      test("delete fails with CountryNotFound for an unknown code") {
        for
          xa    <- ZIO.service[Transactor[Task]]
          repo   = new DoobieCountryRepository(xa)
          error <- repo.delete(CountryCode("YY")).flip
        yield assertTrue(error == DomainError.CountryNotFound("YY"))
      }
    ).provideLayerShared(PostgresContainerSupport.transactorLayer)
}
