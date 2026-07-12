package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.country.Country
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait FindCountryForAirportUseCase:
  def findCountry(iata: IataCode): IO[DomainError, Country]
