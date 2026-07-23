package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.error.DomainError
import zio.*

final case class SyncPlan[K, E](toCreate: List[E], toUpdate: List[E], toDelete: List[K], unchanged: Int)

object EntitySync:

  /** Fetches every row currently stored for an entity and turns the list into a
    * lookup table keyed by its natural key, so `reconcile` can look up "is this
    * source row already in the database?" in constant time instead of scanning
    * a list.
    */
  def loadExisting[K, E](findAll: UIO[List[E]], keyOf: E => K): UIO[Map[K, E]] =
    findAll.map(_.map(e => keyOf(e) -> e).toMap)

  /** Compares the freshly downloaded source rows against what's already stored
    * and decides, for every row, what needs to happen: a source row with a key
    * not in `existing` must be created; a source row whose key is in `existing`
    * but whose content differs must be updated; a stored row whose key no
    * longer appears in the source at all must be deleted; everything else is
    * left alone and just counted as unchanged. This is a pure, in-memory diff —
    * it doesn't talk to a database or call any use case itself, it only decides
    * what to do.
    */
  def reconcile[K, E](source: List[E], existing: Map[K, E], keyOf: E => K): SyncPlan[K, E] =
    val sourceKeys = source.map(keyOf).toSet
    val toCreate   = source.filterNot(e => existing.contains(keyOf(e)))
    val toUpdate   = source.filter(e => existing.get(keyOf(e)).exists(_ != e))
    val unchanged  = source.count(e => existing.get(keyOf(e)).contains(e))
    val toDelete   = existing.keySet.diff(sourceKeys).toList
    SyncPlan(toCreate, toUpdate, toDelete, unchanged)

  /** Carries out the plan `reconcile` produced by actually calling the entity's
    * `create`/`update`/ `delete` functions for every row in the corresponding
    * bucket, then summarizes what happened in a `SyncReport`. A single row
    * failing (say, a database conflict) is logged as a warning and counted as a
    * skipped conflict rather than aborting the whole run — one bad row should
    * never stop the rest of the sync from completing.
    */
  def apply[K, E](
      plan: SyncPlan[K, E],
      create: E => IO[DomainError, Unit],
      update: E => IO[DomainError, Unit],
      delete: K => IO[DomainError, Unit]
  ): UIO[SyncReport] =
    for
      createResults <- ZIO.foreach(plan.toCreate)(runLogged("create", _)(create))
      updateResults <- ZIO.foreach(plan.toUpdate)(runLogged("update", _)(update))
      deleteResults <- ZIO.foreach(plan.toDelete)(runLogged("delete", _)(delete))
    yield SyncReport(
      created = createResults.count(identity),
      updated = updateResults.count(identity),
      deleted = deleteResults.count(identity),
      unchanged = plan.unchanged,
      skippedInvalid = 0,
      skippedConflict = (createResults ++ updateResults ++ deleteResults).count(!_)
    )

  /** Runs a single create/update/delete call for one row, and turns a failure
    * into a logged warning plus `false` instead of letting it fail the whole
    * batch — the building block that makes `apply` tolerant of individual row
    * failures.
    */
  private def runLogged[A](action: String, item: A)(f: A => IO[DomainError, Unit]): UIO[Boolean] =
    f(item).foldZIO(
      error => ZIO.logWarning(s"Skipped $action for $item: $error").as(false),
      _ => ZIO.succeed(true)
    )
