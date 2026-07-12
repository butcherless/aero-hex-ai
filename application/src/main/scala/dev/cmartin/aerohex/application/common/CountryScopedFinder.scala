package dev.cmartin.aerohex.application.common

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO}

// Shared by every "find all X registered in a country" service (Airport, Airline, ...): checks
// the country exists before delegating to the entity's own repository, logging under the
// caller's own class name. Mirrors the persistence layer's QuillCountryIdResolver/
// QuillAirlineIdResolver mixin-trait pattern for the same shape of cross-cutting, country-scoped
// lookup.
private[application] trait CountryScopedFinder:
  protected def countryRepository: CountryRepository

  protected def findByCountryChecked[E](code: CountryCode, pagination: Pagination, serviceName: String)(
      findEntities: (CountryCode, Pagination) => IO[DomainError, List[E]]
  ): IO[DomainError, List[E]] =
    countryRepository.findByCode(code).flatMap {
      case None    => ZIO.fail(DomainError.CountryNotFound(code.value))
      case Some(_) => findEntities(code, pagination)
    } @@ ServiceAspect.logged(s"$serviceName.findByCountry(${code.value})")
