package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.infrastructure.persistence.quill.aircraft.QuillAircraftRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import dev.cmartin.aerohex.it.support.{AircraftRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object QuillAircraftRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillAircraftRepository")(AircraftRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.dataSourceLayer >>>
          (QuillAircraftRepository.layer ++ QuillAirlineRepository.layer ++ QuillCountryRepository.layer)
      )
}
