package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.country.{Country, CountryCode, CountryRepository}
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import dev.cmartin.aerohex.it.support.{CountryRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object QuillCountryRepositoryItSpec extends ZIOSpecDefault {

  private val quillOnlyTests: List[Spec[CountryRepository, Any]] = List(
    test("save fails with CountryAlreadyExists on a duplicate code") {
      for
        repo  <- ZIO.service[CountryRepository]
        _     <- repo.save(Country(CountryCode("DE"), "Germany"))
        error <- repo.save(Country(CountryCode("DE"), "Deutschland")).flip
      yield assertTrue(error == DomainError.CountryAlreadyExists("DE"))
    }
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillCountryRepository")((CountryRepositoryContractSpec.tests ++ quillOnlyTests)*)
      .provideLayerShared(PostgresContainerSupport.dataSourceLayer >>> QuillCountryRepository.layer)
}
