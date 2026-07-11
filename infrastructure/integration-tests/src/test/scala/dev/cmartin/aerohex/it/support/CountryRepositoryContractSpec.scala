package dev.cmartin.aerohex.it.support

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.ZIO
import zio.test.*

// Behavior contract shared by QuillCountryRepositoryItSpec and DoobieCountryRepositoryItSpec.
// Quill's save fails on a duplicate code while Doobie's save upserts — a pre-existing, deliberate
// divergence (persistence-postgres is unwired/legacy; see CLAUDE.md's persistence policy), so the
// duplicate-save test stays adapter-specific rather than living here.
object CountryRepositoryContractSpec:

  def tests: List[Spec[CountryRepository, Any]] = List(
    test("isValidCode returns true for a real ISO 3166-1 alpha-2 code") {
      for
        repo  <- ZIO.service[CountryRepository]
        valid <- repo.isValidCode(CountryCode("JP"))
      yield assertTrue(valid)
    },
    test("isValidCode returns false for a code that is not a real ISO 3166-1 alpha-2 code") {
      for
        repo  <- ZIO.service[CountryRepository]
        valid <- repo.isValidCode(CountryCode("ZZ"))
      yield assertTrue(!valid)
    },
    test("saves and finds a country by code") {
      for
        repo  <- ZIO.service[CountryRepository]
        spain  = Country(CountryCode("ES"), "Spain")
        saved <- repo.save(spain)
        found <- repo.findByCode(CountryCode("ES"))
      yield assertTrue(saved == spain, found.contains(spain))
    },
    test("findAll includes saved countries") {
      for
        repo <- ZIO.service[CountryRepository]
        _    <- repo.save(Country(CountryCode("FR"), "France"))
        all  <- repo.findAll(Pagination(page = 1, pageSize = 100))
      yield assertTrue(all.exists(_.code.value == "FR"))
    },
    test("searchByName matches a case-insensitive substring") {
      for
        repo    <- ZIO.service[CountryRepository]
        _       <- repo.save(Country(CountryCode("IT"), "Italy"))
        results <- repo.searchByName("ital")
      yield assertTrue(results.exists(_.code.value == "IT"))
    },
    test("update changes the name of an existing country") {
      for
        repo    <- ZIO.service[CountryRepository]
        _       <- repo.save(Country(CountryCode("PT"), "Portugal"))
        updated <- repo.update(Country(CountryCode("PT"), "Republica Portuguesa"))
        found   <- repo.findByCode(CountryCode("PT"))
      yield assertTrue(updated.name == "Republica Portuguesa", found.map(_.name).contains("Republica Portuguesa"))
    },
    test("update fails with CountryNotFound for an unknown code") {
      for
        repo  <- ZIO.service[CountryRepository]
        error <- repo.update(Country(CountryCode("ZZ"), "Nowhere")).flip
      yield assertTrue(error == DomainError.CountryNotFound("ZZ"))
    },
    test("delete removes an existing country") {
      for
        repo  <- ZIO.service[CountryRepository]
        _     <- repo.save(Country(CountryCode("NL"), "Netherlands"))
        _     <- repo.delete(CountryCode("NL"))
        found <- repo.findByCode(CountryCode("NL"))
      yield assertTrue(found.isEmpty)
    },
    test("delete fails with CountryNotFound for an unknown code") {
      for
        repo  <- ZIO.service[CountryRepository]
        error <- repo.delete(CountryCode("YY")).flip
      yield assertTrue(error == DomainError.CountryNotFound("YY"))
    }
  )
