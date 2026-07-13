package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airport.QuillAirportRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.flight.QuillFlightRepository
import dev.cmartin.aerohex.it.support.{FlightRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object QuillFlightRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillFlightRepository")(FlightRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.dataSourceLayer >>>
          (QuillFlightRepository.layer ++ QuillAirportRepository.layer ++ QuillAirlineRepository.layer ++
            QuillCountryRepository.layer)
      )
}
