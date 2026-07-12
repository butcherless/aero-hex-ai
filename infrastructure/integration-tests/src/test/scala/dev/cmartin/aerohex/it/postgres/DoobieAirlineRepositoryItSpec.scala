package dev.cmartin.aerohex.it.postgres

import dev.cmartin.aerohex.infrastructure.persistence.postgres.airline.DoobieAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.postgres.country.DoobieCountryRepository
import dev.cmartin.aerohex.it.support.{AirlineRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object DoobieAirlineRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DoobieAirlineRepository")(AirlineRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.transactorLayer >>> (DoobieAirlineRepository.layer ++ DoobieCountryRepository.layer)
      )
}
