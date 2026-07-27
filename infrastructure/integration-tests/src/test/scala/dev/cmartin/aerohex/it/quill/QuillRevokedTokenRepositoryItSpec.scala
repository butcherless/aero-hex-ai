package dev.cmartin.aerohex.it.quill

import dev.cmartin.aerohex.infrastructure.persistence.quill.user.QuillRevokedTokenRepository
import dev.cmartin.aerohex.it.support.{PostgresContainerSupport, RevokedTokenRepositoryContractSpec}
import zio.*
import zio.test.*

object QuillRevokedTokenRepositoryItSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("QuillRevokedTokenRepository")(RevokedTokenRepositoryContractSpec.tests*)
      .provideLayerShared(PostgresContainerSupport.dataSourceLayer >>> QuillRevokedTokenRepository.layer)
}
