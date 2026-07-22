package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.*
import zio.test.*

object EntitySyncSpec extends ZIOSpecDefault:

  private val spain  = Country(CountryCode("ES"), "Spain")
  private val france = Country(CountryCode("FR"), "France")
  private val italy  = Country(CountryCode("IT"), "Italy")

  private def keyOf(c: Country): CountryCode = c.code

  private val noop: Country => IO[DomainError, Unit]           = _ => ZIO.unit
  private val noopDelete: CountryCode => IO[DomainError, Unit] = _ => ZIO.unit

  private def failingCreate(c: Country): IO[DomainError, Unit] =
    ZIO.fail(DomainError.CountryAlreadyExists(c.code.value))

  private def failingUpdate(c: Country): IO[DomainError, Unit] =
    ZIO.fail(DomainError.CountryNotFound(c.code.value))

  private def failingDelete(k: CountryCode): IO[DomainError, Unit] =
    ZIO.fail(DomainError.CountryNotFound(k.value))

  override def spec: Spec[TestEnvironment, Any] =
    suite("EntitySync")(
      test("loadExisting keys every row by its natural key") {
        for existing <- EntitySync.loadExisting(ZIO.succeed(List(spain, france)), keyOf)
        yield assertTrue(existing == Map(spain.code -> spain, france.code -> france))
      },
      test("reconcile buckets a source-only row as a create, leaving the matched row unchanged") {
        val plan = EntitySync.reconcile(List(spain, france), Map(spain.code -> spain), keyOf)
        assertTrue(
          plan.toCreate == List(france),
          plan.toUpdate.isEmpty,
          plan.toDelete.isEmpty,
          plan.unchanged == 1
        )
      },
      test("reconcile buckets a structurally-different existing row as an update") {
        val updatedSpain = spain.copy(name = "Reino de España")
        val plan         = EntitySync.reconcile(List(updatedSpain), Map(spain.code -> spain), keyOf)
        assertTrue(
          plan.toCreate.isEmpty,
          plan.toUpdate == List(updatedSpain),
          plan.toDelete.isEmpty,
          plan.unchanged == 0
        )
      },
      test("reconcile buckets an existing-only key as a delete") {
        val plan = EntitySync.reconcile(List(spain), Map(spain.code -> spain, italy.code -> italy), keyOf)
        assertTrue(
          plan.toCreate.isEmpty,
          plan.toUpdate.isEmpty,
          plan.toDelete == List(italy.code),
          plan.unchanged == 1
        )
      },
      test("reconcile leaves an identical row unchanged, not created or updated") {
        val plan = EntitySync.reconcile(List(spain), Map(spain.code -> spain), keyOf)
        assertTrue(plan.toCreate.isEmpty, plan.toUpdate.isEmpty, plan.toDelete.isEmpty, plan.unchanged == 1)
      },
      test("apply runs every create/update/delete and reports the resulting counts") {
        val plan =
          SyncPlan(toCreate = List(france), toUpdate = List(spain), toDelete = List(italy.code), unchanged = 1)
        for report <- EntitySync.apply(plan, noop, noop, noopDelete)
        yield assertTrue(
          report == SyncReport(
            created = 1,
            updated = 1,
            deleted = 1,
            unchanged = 1,
            skippedInvalid = 0,
            skippedConflict = 0
          )
        )
      },
      test(
        "apply catches a failing create/update/delete, logs it, and counts it as skippedConflict instead of failing the suite"
      ) {
        val plan =
          SyncPlan(toCreate = List(france), toUpdate = List(spain), toDelete = List(italy.code), unchanged = 0)
        for report <- EntitySync.apply(plan, failingCreate, failingUpdate, failingDelete)
        yield assertTrue(
          report == SyncReport(
            created = 0,
            updated = 0,
            deleted = 0,
            unchanged = 0,
            skippedInvalid = 0,
            skippedConflict = 3
          )
        )
      }
    )
