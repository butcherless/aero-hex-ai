package dev.cmartin.aerohex.application.airport

import dev.cmartin.aerohex.domain.airport.{Airport, AirportRepository, IataCode}
import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO}

// Repository stub where every method dies unless overridden — any call a test doesn't
// expect surfaces as a loud failure instead of a silently wrong default. stubAirportRepo
// lets each test override just the one method it exercises instead of re-declaring the
// full interface.
private[application] object AirportRepositoryStub:

  val unimplementedAirportRepo: AirportRepository = new AirportRepository:
    def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 =
      ZIO.die(new NotImplementedError("findByIata"))
    def findAll(p: Pagination): IO[DomainError, List[Airport]]                       =
      ZIO.die(new NotImplementedError("findAll"))
    def findAllUnbounded: IO[DomainError, List[Airport]]                             =
      ZIO.die(new NotImplementedError("findAllUnbounded"))
    def searchByName(q: String): IO[DomainError, List[Airport]]                      =
      ZIO.die(new NotImplementedError("searchByName"))
    def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
      ZIO.die(new NotImplementedError("findByCountry"))
    def findCountryByIata(iata: IataCode): IO[DomainError, Option[Country]]          =
      ZIO.die(new NotImplementedError("findCountryByIata"))
    def save(a: Airport, c: CountryCode): IO[DomainError, Airport]                   =
      ZIO.die(new NotImplementedError("save"))
    def update(a: Airport, c: CountryCode): IO[DomainError, Airport]                 =
      ZIO.die(new NotImplementedError("update"))
    def delete(iata: IataCode): IO[DomainError, Unit]                                =
      ZIO.die(new NotImplementedError("delete"))

  def stubAirportRepo(
      onFindByIata: IataCode => IO[DomainError, Option[Airport]] = unimplementedAirportRepo.findByIata,
      onFindAll: Pagination => IO[DomainError, List[Airport]] = unimplementedAirportRepo.findAll,
      onFindAllUnbounded: IO[DomainError, List[Airport]] = unimplementedAirportRepo.findAllUnbounded,
      onSearchByName: String => IO[DomainError, List[Airport]] = unimplementedAirportRepo.searchByName,
      onFindByCountry: (CountryCode, Pagination) => IO[DomainError, List[Airport]] =
        unimplementedAirportRepo.findByCountry,
      onFindCountryByIata: IataCode => IO[DomainError, Option[Country]] = unimplementedAirportRepo.findCountryByIata,
      onSave: (Airport, CountryCode) => IO[DomainError, Airport] = unimplementedAirportRepo.save,
      onUpdate: (Airport, CountryCode) => IO[DomainError, Airport] = unimplementedAirportRepo.update,
      onDelete: IataCode => IO[DomainError, Unit] = unimplementedAirportRepo.delete
  ): AirportRepository = new AirportRepository:
    def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = onFindByIata(iata)
    def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = onFindAll(p)
    def findAllUnbounded: IO[DomainError, List[Airport]]                             = onFindAllUnbounded
    def searchByName(q: String): IO[DomainError, List[Airport]]                      = onSearchByName(q)
    def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] = onFindByCountry(c, p)
    def findCountryByIata(iata: IataCode): IO[DomainError, Option[Country]]          = onFindCountryByIata(iata)
    def save(a: Airport, c: CountryCode): IO[DomainError, Airport]                   = onSave(a, c)
    def update(a: Airport, c: CountryCode): IO[DomainError, Airport]                 = onUpdate(a, c)
    def delete(iata: IataCode): IO[DomainError, Unit]                                = onDelete(iata)
