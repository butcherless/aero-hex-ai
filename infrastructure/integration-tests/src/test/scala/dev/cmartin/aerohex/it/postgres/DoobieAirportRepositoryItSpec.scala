package dev.cmartin.aerohex.it.postgres

import dev.cmartin.aerohex.infrastructure.persistence.postgres.repository.{
  DoobieAirportRepository, DoobieCountryRepository
}
import dev.cmartin.aerohex.it.support.{AirportRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object DoobieAirportRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DoobieAirportRepository")(AirportRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.transactorLayer >>> (DoobieAirportRepository.layer ++ DoobieCountryRepository.layer)
      )
}
