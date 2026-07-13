package dev.cmartin.aerohex.it.postgres

import dev.cmartin.aerohex.infrastructure.persistence.postgres.airline.DoobieAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.postgres.airport.DoobieAirportRepository
import dev.cmartin.aerohex.infrastructure.persistence.postgres.country.DoobieCountryRepository
import dev.cmartin.aerohex.infrastructure.persistence.postgres.flight.DoobieFlightRepository
import dev.cmartin.aerohex.it.support.{FlightRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object DoobieFlightRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DoobieFlightRepository")(FlightRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.transactorLayer >>>
          (DoobieFlightRepository.layer ++ DoobieAirportRepository.layer ++ DoobieAirlineRepository.layer ++
            DoobieCountryRepository.layer)
      )
}
