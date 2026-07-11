package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.infrastructure.persistence.quill.repository.{
  QuillAircraftRepository,
  QuillAirlineRepository,
  QuillCountryRepository
}
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
