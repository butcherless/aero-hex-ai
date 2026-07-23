package dev.cmartin.aerohex.application.airline

import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode, AirlineRepository}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO}

// Repository stub where every method dies unless overridden — any call a test doesn't
// expect surfaces as a loud failure instead of a silently wrong default. stubAirlineRepo
// lets each test override just the one method it exercises instead of re-declaring the
// full interface.
private[application] object AirlineRepositoryStub:

  val unimplementedAirlineRepo: AirlineRepository = new AirlineRepository:
    def findByIcao(icao: AirlineIcaoCode): IO[DomainError, Option[Airline]]          =
      ZIO.die(new NotImplementedError("findByIcao"))
    def findAll(p: Pagination): IO[DomainError, List[Airline]]                       =
      ZIO.die(new NotImplementedError("findAll"))
    def findAllUnbounded: IO[DomainError, List[Airline]]                             =
      ZIO.die(new NotImplementedError("findAllUnbounded"))
    def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airline]] =
      ZIO.die(new NotImplementedError("findByCountry"))
    def save(a: Airline, c: CountryCode): IO[DomainError, Airline]                   =
      ZIO.die(new NotImplementedError("save"))
    def update(a: Airline, c: CountryCode): IO[DomainError, Airline]                 =
      ZIO.die(new NotImplementedError("update"))
    def delete(icao: AirlineIcaoCode): IO[DomainError, Unit]                         =
      ZIO.die(new NotImplementedError("delete"))

  def stubAirlineRepo(
      onFindByIcao: AirlineIcaoCode => IO[DomainError, Option[Airline]] = unimplementedAirlineRepo.findByIcao,
      onFindAll: Pagination => IO[DomainError, List[Airline]] = unimplementedAirlineRepo.findAll,
      onFindAllUnbounded: IO[DomainError, List[Airline]] = unimplementedAirlineRepo.findAllUnbounded,
      onFindByCountry: (CountryCode, Pagination) => IO[DomainError, List[Airline]] =
        unimplementedAirlineRepo.findByCountry,
      onSave: (Airline, CountryCode) => IO[DomainError, Airline] = unimplementedAirlineRepo.save,
      onUpdate: (Airline, CountryCode) => IO[DomainError, Airline] = unimplementedAirlineRepo.update,
      onDelete: AirlineIcaoCode => IO[DomainError, Unit] = unimplementedAirlineRepo.delete
  ): AirlineRepository = new AirlineRepository:
    def findByIcao(icao: AirlineIcaoCode): IO[DomainError, Option[Airline]]          = onFindByIcao(icao)
    def findAll(p: Pagination): IO[DomainError, List[Airline]]                       = onFindAll(p)
    def findAllUnbounded: IO[DomainError, List[Airline]]                             = onFindAllUnbounded
    def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airline]] = onFindByCountry(c, p)
    def save(a: Airline, c: CountryCode): IO[DomainError, Airline]                   = onSave(a, c)
    def update(a: Airline, c: CountryCode): IO[DomainError, Airline]                 = onUpdate(a, c)
    def delete(icao: AirlineIcaoCode): IO[DomainError, Unit]                         = onDelete(icao)
