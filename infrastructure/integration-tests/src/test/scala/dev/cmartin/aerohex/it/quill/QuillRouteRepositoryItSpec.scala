package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.infrastructure.persistence.quill.airport.QuillAirportRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.route.QuillRouteRepository
import dev.cmartin.aerohex.it.support.{PostgresContainerSupport, RouteRepositoryContractSpec}
import zio.*
import zio.test.*

object QuillRouteRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillRouteRepository")(RouteRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.dataSourceLayer >>>
          (QuillRouteRepository.layer ++ QuillAirportRepository.layer ++ QuillCountryRepository.layer)
      )
}
