package dev.cmartin.aerohex.infrastructure.masterdata

import zio.*

final case class SyncReport(
    created: Int,
    updated: Int,
    deleted: Int,
    unchanged: Int,
    skippedInvalid: Int,
    skippedConflict: Int
):

  def log(): UIO[Unit] =
    ZIO.logInfo(
      s"Sync report — created: $created, updated: $updated, deleted: $deleted, " +
        s"unchanged: $unchanged, skippedInvalid: $skippedInvalid, skippedConflict: $skippedConflict"
    )
