package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, CountryCode}
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindAirportsByCountryUseCase:
  def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]]
