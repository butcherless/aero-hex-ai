package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import dev.cmartin.aerohex.it.support.{AirlineRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object QuillAirlineRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillAirlineRepository")(AirlineRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.dataSourceLayer >>> (QuillAirlineRepository.layer ++ QuillCountryRepository.layer)
      )
}
