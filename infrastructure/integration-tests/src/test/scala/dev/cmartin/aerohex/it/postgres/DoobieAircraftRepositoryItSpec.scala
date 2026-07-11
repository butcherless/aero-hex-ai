package dev.cmartin.aerohex.it.postgres

import dev.cmartin.aerohex.infrastructure.persistence.postgres.repository.{
  DoobieAircraftRepository,
  DoobieAirlineRepository,
  DoobieCountryRepository
}
import dev.cmartin.aerohex.it.support.{AircraftRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object DoobieAircraftRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DoobieAircraftRepository")(AircraftRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.transactorLayer >>>
          (DoobieAircraftRepository.layer ++ DoobieAirlineRepository.layer ++ DoobieCountryRepository.layer)
      )
}
