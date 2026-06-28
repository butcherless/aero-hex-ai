package domain.port.in

import domain.error.DomainError
import domain.model.Journey
import shared.Pagination
import zio.IO

trait FindJourneyUseCase {
  def findById(id: String): IO[DomainError, Journey]
  def findAll(pagination: Pagination): IO[DomainError, List[Journey]]
}
