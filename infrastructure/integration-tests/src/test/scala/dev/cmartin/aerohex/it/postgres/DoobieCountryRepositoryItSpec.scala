package dev.cmartin.aerohex.it.postgres

import dev.cmartin.aerohex.infrastructure.persistence.postgres.repository.DoobieCountryRepository
import dev.cmartin.aerohex.it.support.{CountryRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object DoobieCountryRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DoobieCountryRepository")(CountryRepositoryContractSpec.tests*)
      .provideLayerShared(PostgresContainerSupport.transactorLayer >>> DoobieCountryRepository.layer)
}
