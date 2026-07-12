package dev.cmartin.aerohex.application.country

import dev.cmartin.aerohex.domain.country.{Country, CountryCode, CountryRepository}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO, ZIO}

// Repository stub where every method dies unless overridden — any call a test doesn't
// expect surfaces as a loud failure instead of a silently wrong default. stubCountryRepo
// lets each test override just the one method it exercises instead of re-declaring the
// full interface.
private[application] object CountryRepositoryStub:

  val unimplementedCountryRepo: CountryRepository = new CountryRepository:
    def validateCode(code: CountryCode): IO[DomainError, Unit]          =
      ZIO.die(new NotImplementedError("validateCode"))
    def findByCode(code: CountryCode): IO[DomainError, Option[Country]] =
      ZIO.die(new NotImplementedError("findByCode"))
    def findAll(p: Pagination): UIO[List[Country]]                      =
      ZIO.die(new NotImplementedError("findAll"))
    def searchByName(q: String): UIO[List[Country]]                     =
      ZIO.die(new NotImplementedError("searchByName"))
    def save(c: Country): IO[DomainError, Country]                      =
      ZIO.die(new NotImplementedError("save"))
    def update(c: Country): IO[DomainError, Country]                    =
      ZIO.die(new NotImplementedError("update"))
    def delete(code: CountryCode): IO[DomainError, Unit]                =
      ZIO.die(new NotImplementedError("delete"))

  def stubCountryRepo(
      onValidateCode: CountryCode => IO[DomainError, Unit] = unimplementedCountryRepo.validateCode,
      onFindByCode: CountryCode => IO[DomainError, Option[Country]] = unimplementedCountryRepo.findByCode,
      onFindAll: Pagination => UIO[List[Country]] = unimplementedCountryRepo.findAll,
      onSearchByName: String => UIO[List[Country]] = unimplementedCountryRepo.searchByName,
      onSave: Country => IO[DomainError, Country] = unimplementedCountryRepo.save,
      onUpdate: Country => IO[DomainError, Country] = unimplementedCountryRepo.update,
      onDelete: CountryCode => IO[DomainError, Unit] = unimplementedCountryRepo.delete
  ): CountryRepository = new CountryRepository:
    def validateCode(code: CountryCode): IO[DomainError, Unit]          = onValidateCode(code)
    def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = onFindByCode(code)
    def findAll(p: Pagination): UIO[List[Country]]                      = onFindAll(p)
    def searchByName(q: String): UIO[List[Country]]                     = onSearchByName(q)
    def save(c: Country): IO[DomainError, Country]                      = onSave(c)
    def update(c: Country): IO[DomainError, Country]                    = onUpdate(c)
    def delete(code: CountryCode): IO[DomainError, Unit]                = onDelete(code)
