package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airline, Airport, Country, CountryCode, IataCode, IcaoCode}
import dev.cmartin.aerohex.domain.port.out.{AirlineRepository, AirportRepository, CountryRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO, ZIO}

// Repository stubs where every method dies unless overridden — any call a test doesn't
// expect surfaces as a loud failure instead of a silently wrong default. stubAirportRepo/
// stubCountryRepo let each test override just the one method it exercises instead of
// re-declaring the full interface.
private[service] object RepositoryStubs:

  val unimplementedAirportRepo: AirportRepository = new AirportRepository:
    def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 =
      ZIO.die(new NotImplementedError("findByIata"))
    def findAll(p: Pagination): IO[DomainError, List[Airport]]                       =
      ZIO.die(new NotImplementedError("findAll"))
    def searchByName(q: String): IO[DomainError, List[Airport]]                      =
      ZIO.die(new NotImplementedError("searchByName"))
    def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
      ZIO.die(new NotImplementedError("findByCountry"))
    def save(a: Airport, c: CountryCode): IO[DomainError, Airport]                   =
      ZIO.die(new NotImplementedError("save"))
    def update(a: Airport, c: CountryCode): IO[DomainError, Airport]                 =
      ZIO.die(new NotImplementedError("update"))
    def delete(iata: IataCode): IO[DomainError, Unit]                                =
      ZIO.die(new NotImplementedError("delete"))

  val unimplementedAirlineRepo: AirlineRepository = new AirlineRepository:
    def findByIcao(icao: IcaoCode): IO[DomainError, Option[Airline]] =
      ZIO.die(new NotImplementedError("findByIcao"))
    def findAll(p: Pagination): IO[DomainError, List[Airline]]       =
      ZIO.die(new NotImplementedError("findAll"))
    def save(a: Airline, c: CountryCode): IO[DomainError, Airline]   =
      ZIO.die(new NotImplementedError("save"))
    def update(a: Airline, c: CountryCode): IO[DomainError, Airline] =
      ZIO.die(new NotImplementedError("update"))
    def delete(icao: IcaoCode): IO[DomainError, Unit]                =
      ZIO.die(new NotImplementedError("delete"))

  val unimplementedCountryRepo: CountryRepository = new CountryRepository:
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

  def stubAirportRepo(
      onFindByIata: IataCode => IO[DomainError, Option[Airport]] = unimplementedAirportRepo.findByIata,
      onFindAll: Pagination => IO[DomainError, List[Airport]] = unimplementedAirportRepo.findAll,
      onSearchByName: String => IO[DomainError, List[Airport]] = unimplementedAirportRepo.searchByName,
      onFindByCountry: (CountryCode, Pagination) => IO[DomainError, List[Airport]] =
        unimplementedAirportRepo.findByCountry,
      onSave: (Airport, CountryCode) => IO[DomainError, Airport] = unimplementedAirportRepo.save,
      onUpdate: (Airport, CountryCode) => IO[DomainError, Airport] = unimplementedAirportRepo.update,
      onDelete: IataCode => IO[DomainError, Unit] = unimplementedAirportRepo.delete
  ): AirportRepository = new AirportRepository:
    def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = onFindByIata(iata)
    def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = onFindAll(p)
    def searchByName(q: String): IO[DomainError, List[Airport]]                      = onSearchByName(q)
    def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] = onFindByCountry(c, p)
    def save(a: Airport, c: CountryCode): IO[DomainError, Airport]                   = onSave(a, c)
    def update(a: Airport, c: CountryCode): IO[DomainError, Airport]                 = onUpdate(a, c)
    def delete(iata: IataCode): IO[DomainError, Unit]                                = onDelete(iata)

  def stubAirlineRepo(
      onFindByIcao: IcaoCode => IO[DomainError, Option[Airline]] = unimplementedAirlineRepo.findByIcao,
      onFindAll: Pagination => IO[DomainError, List[Airline]] = unimplementedAirlineRepo.findAll,
      onSave: (Airline, CountryCode) => IO[DomainError, Airline] = unimplementedAirlineRepo.save,
      onUpdate: (Airline, CountryCode) => IO[DomainError, Airline] = unimplementedAirlineRepo.update,
      onDelete: IcaoCode => IO[DomainError, Unit] = unimplementedAirlineRepo.delete
  ): AirlineRepository = new AirlineRepository:
    def findByIcao(icao: IcaoCode): IO[DomainError, Option[Airline]] = onFindByIcao(icao)
    def findAll(p: Pagination): IO[DomainError, List[Airline]]       = onFindAll(p)
    def save(a: Airline, c: CountryCode): IO[DomainError, Airline]   = onSave(a, c)
    def update(a: Airline, c: CountryCode): IO[DomainError, Airline] = onUpdate(a, c)
    def delete(icao: IcaoCode): IO[DomainError, Unit]                = onDelete(icao)

  def stubCountryRepo(
      onFindByCode: CountryCode => IO[DomainError, Option[Country]] = unimplementedCountryRepo.findByCode,
      onFindAll: Pagination => UIO[List[Country]] = unimplementedCountryRepo.findAll,
      onSearchByName: String => UIO[List[Country]] = unimplementedCountryRepo.searchByName,
      onSave: Country => IO[DomainError, Country] = unimplementedCountryRepo.save,
      onUpdate: Country => IO[DomainError, Country] = unimplementedCountryRepo.update,
      onDelete: CountryCode => IO[DomainError, Unit] = unimplementedCountryRepo.delete
  ): CountryRepository = new CountryRepository:
    def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = onFindByCode(code)
    def findAll(p: Pagination): UIO[List[Country]]                      = onFindAll(p)
    def searchByName(q: String): UIO[List[Country]]                     = onSearchByName(q)
    def save(c: Country): IO[DomainError, Country]                      = onSave(c)
    def update(c: Country): IO[DomainError, Country]                    = onUpdate(c)
    def delete(code: CountryCode): IO[DomainError, Unit]                = onDelete(code)
