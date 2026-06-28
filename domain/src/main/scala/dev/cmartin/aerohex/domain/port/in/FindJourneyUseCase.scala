package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Journey
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindJourneyUseCase {
  def findById(id: String): IO[DomainError, Journey]
  def findAll(pagination: Pagination): IO[DomainError, List[Journey]]
}
