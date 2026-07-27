package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.infrastructure.persistence.quill.user.QuillUserRepository
import dev.cmartin.aerohex.it.support.{PostgresContainerSupport, UserRepositoryContractSpec}
import javax.sql.DataSource
import zio.*
import zio.test.*

object QuillUserRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillUserRepository")(UserRepositoryContractSpec.tests*)
      .provideLayerShared(
        PostgresContainerSupport.dataSourceLayer >>> (QuillUserRepository.layer ++ ZLayer.service[DataSource])
      )
}
