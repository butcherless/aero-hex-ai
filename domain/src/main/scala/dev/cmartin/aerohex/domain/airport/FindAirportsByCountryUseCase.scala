package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindAirportsByCountryUseCase:
  def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]]
