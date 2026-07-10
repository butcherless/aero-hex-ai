package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.infrastructure.persistence.quill.repository.{QuillAirportRepository, QuillCountryRepository}
import dev.cmartin.aerohex.it.support.{AirportRepositoryContractSpec, PostgresContainerSupport}
import zio.*
import zio.test.*

object QuillAirportRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillAirportRepository")(AirportRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.dataSourceLayer >>> (QuillAirportRepository.layer ++ QuillCountryRepository.layer)
      )
}
