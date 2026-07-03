package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, Country, CountryCode, IataCode}
import dev.cmartin.aerohex.domain.port.in.{
  CreateAirportCommand,
  CreateAirportUseCase,
  FindAirportUseCase,
  FindAirportsByCountryUseCase,
  UpdateAirportCommand,
  UpdateAirportUseCase
}
import dev.cmartin.aerohex.domain.port.out.{AirportRepository, CountryRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, Ref, Scope, UIO, ZIO, ZLayer}
import zio.test.*

object AirportServiceSpec extends ZIOSpecDefault:

  private val madrid    = Airport(IataCode("MAD"), "LEMD", "Adolfo Suárez Madrid-Barajas", "Madrid", CountryCode("ES"))
  private val barcelona =
    Airport(IataCode("BCN"), "LEBL", "Josep Tarradellas Barcelona-El Prat", "Barcelona", CountryCode("ES"))
  private val spain     = Country(CountryCode("ES"), "Spain")

  // Repository stubs where every method dies unless overridden — any call the test
  // doesn't expect surfaces as a loud failure instead of a silently wrong default.
  private val unimplementedAirportRepo: AirportRepository = new AirportRepository:
    def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 =
      ZIO.die(new NotImplementedError("findByIata"))
    def findAll(p: Pagination): IO[DomainError, List[Airport]]                       =
      ZIO.die(new NotImplementedError("findAll"))
    def searchByName(q: String): IO[DomainError, List[Airport]]                      =
      ZIO.die(new NotImplementedError("searchByName"))
    def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
      ZIO.die(new NotImplementedError("findByCountry"))
    def save(a: Airport): IO[DomainError, Airport]                                   =
      ZIO.die(new NotImplementedError("save"))
    def update(a: Airport): IO[DomainError, Airport]                                 =
      ZIO.die(new NotImplementedError("update"))
    def delete(iata: IataCode): IO[DomainError, Unit]                                =
      ZIO.die(new NotImplementedError("delete"))

  private val unimplementedCountryRepo: CountryRepository = new CountryRepository:
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

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Airport application services")(
      suite("CreateAirportService")(
        test("saves and returns the new airport when it does not already exist") {
          for
            savedRef <- Ref.make[Option[Airport]](None)
            repo      = new AirportRepository:
                          def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = ZIO.none
                          def findAll(p: Pagination): IO[DomainError, List[Airport]]                       =
                            unimplementedAirportRepo.findAll(p)
                          def searchByName(q: String): IO[DomainError, List[Airport]]                      =
                            unimplementedAirportRepo.searchByName(q)
                          def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
                            unimplementedAirportRepo.findByCountry(c, p)
                          def save(a: Airport): IO[DomainError, Airport]                                   =
                            savedRef.set(Some(a)).as(a)
                          def update(a: Airport): IO[DomainError, Airport]                                 =
                            unimplementedAirportRepo.update(a)
                          def delete(iata: IataCode): IO[DomainError, Unit]                                =
                            unimplementedAirportRepo.delete(iata)
            command   =
              CreateAirportCommand(IataCode("MAD"), "LEMD", "Adolfo Suárez Madrid-Barajas", "Madrid", CountryCode("ES"))
            result   <- new CreateAirportService(repo).create(command)
            saved    <- savedRef.get
          yield assertTrue(
            result == madrid,
            saved.contains(madrid)
          )
        },
        test("fails with AirportAlreadyExists and never calls save when the airport already exists") {
          val repo    = new AirportRepository:
            def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = ZIO.some(madrid)
            def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = unimplementedAirportRepo.findAll(p)
            def searchByName(q: String): IO[DomainError, List[Airport]]                      =
              unimplementedAirportRepo.searchByName(q)
            def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
              unimplementedAirportRepo.findByCountry(c, p)
            def save(a: Airport): IO[DomainError, Airport]                                   = unimplementedAirportRepo.save(a)
            def update(a: Airport): IO[DomainError, Airport]                                 =
              unimplementedAirportRepo.update(a)
            def delete(iata: IataCode): IO[DomainError, Unit]                                =
              unimplementedAirportRepo.delete(iata)
          val command = CreateAirportCommand(IataCode("MAD"), "LEMD", "Other name", "Madrid", CountryCode("ES"))
          for error <- new CreateAirportService(repo).create(command).flip
          yield assertTrue(error == DomainError.AirportAlreadyExists("MAD"))
        }
      ),
      suite("FindAirportService")(
        test("returns the airport when the repository finds it") {
          val repo = new AirportRepository:
            def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = ZIO.some(madrid)
            def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = unimplementedAirportRepo.findAll(p)
            def searchByName(q: String): IO[DomainError, List[Airport]]                      =
              unimplementedAirportRepo.searchByName(q)
            def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
              unimplementedAirportRepo.findByCountry(c, p)
            def save(a: Airport): IO[DomainError, Airport]                                   = unimplementedAirportRepo.save(a)
            def update(a: Airport): IO[DomainError, Airport]                                 =
              unimplementedAirportRepo.update(a)
            def delete(iata: IataCode): IO[DomainError, Unit]                                =
              unimplementedAirportRepo.delete(iata)
          for result <- new FindAirportService(repo).findByIata("MAD")
          yield assertTrue(result == madrid)
        },
        test("fails with AirportNotFound when the repository has no match") {
          val repo = new AirportRepository:
            def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = ZIO.none
            def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = unimplementedAirportRepo.findAll(p)
            def searchByName(q: String): IO[DomainError, List[Airport]]                      =
              unimplementedAirportRepo.searchByName(q)
            def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
              unimplementedAirportRepo.findByCountry(c, p)
            def save(a: Airport): IO[DomainError, Airport]                                   = unimplementedAirportRepo.save(a)
            def update(a: Airport): IO[DomainError, Airport]                                 =
              unimplementedAirportRepo.update(a)
            def delete(iata: IataCode): IO[DomainError, Unit]                                =
              unimplementedAirportRepo.delete(iata)
          for error <- new FindAirportService(repo).findByIata("XXX").flip
          yield assertTrue(error == DomainError.AirportNotFound("XXX"))
        },
        test("findAll delegates to the repository unchanged") {
          val repo = new AirportRepository:
            def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = unimplementedAirportRepo.findByIata(iata)
            def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = ZIO.succeed(List(madrid, barcelona))
            def searchByName(q: String): IO[DomainError, List[Airport]]                      =
              unimplementedAirportRepo.searchByName(q)
            def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
              unimplementedAirportRepo.findByCountry(c, p)
            def save(a: Airport): IO[DomainError, Airport]                                   = unimplementedAirportRepo.save(a)
            def update(a: Airport): IO[DomainError, Airport]                                 =
              unimplementedAirportRepo.update(a)
            def delete(iata: IataCode): IO[DomainError, Unit]                                =
              unimplementedAirportRepo.delete(iata)
          for result <- new FindAirportService(repo).findAll(Pagination(1, 20))
          yield assertTrue(result == List(madrid, barcelona))
        },
        test("searchByName delegates to the repository unchanged") {
          val repo = new AirportRepository:
            def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = unimplementedAirportRepo.findByIata(iata)
            def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = unimplementedAirportRepo.findAll(p)
            def searchByName(q: String): IO[DomainError, List[Airport]]                      = ZIO.succeed(List(madrid))
            def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
              unimplementedAirportRepo.findByCountry(c, p)
            def save(a: Airport): IO[DomainError, Airport]                                   = unimplementedAirportRepo.save(a)
            def update(a: Airport): IO[DomainError, Airport]                                 =
              unimplementedAirportRepo.update(a)
            def delete(iata: IataCode): IO[DomainError, Unit]                                =
              unimplementedAirportRepo.delete(iata)
          for result <- new FindAirportService(repo).searchByName("Madrid")
          yield assertTrue(result == List(madrid))
        }
      ),
      suite("FindAirportsByCountryService")(
        test("returns the airports in the country when the country exists") {
          val countryRepo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.some(spain)
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplementedCountryRepo.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     =
              unimplementedCountryRepo.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplementedCountryRepo.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplementedCountryRepo.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                =
              unimplementedCountryRepo.delete(code)
          val airportRepo = new AirportRepository:
            def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = unimplementedAirportRepo.findByIata(iata)
            def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = unimplementedAirportRepo.findAll(p)
            def searchByName(q: String): IO[DomainError, List[Airport]]                      =
              unimplementedAirportRepo.searchByName(q)
            def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] = ZIO.succeed(List(madrid))
            def save(a: Airport): IO[DomainError, Airport]                                   = unimplementedAirportRepo.save(a)
            def update(a: Airport): IO[DomainError, Airport]                                 =
              unimplementedAirportRepo.update(a)
            def delete(iata: IataCode): IO[DomainError, Unit]                                =
              unimplementedAirportRepo.delete(iata)
          for result <- new FindAirportsByCountryService(countryRepo, airportRepo)
                          .findByCountry(CountryCode("ES"), Pagination(1, 20))
          yield assertTrue(result == List(madrid))
        },
        test("fails with CountryNotFound and never queries airports when the country does not exist") {
          val countryRepo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.none
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplementedCountryRepo.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     =
              unimplementedCountryRepo.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplementedCountryRepo.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplementedCountryRepo.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                =
              unimplementedCountryRepo.delete(code)
          for error <- new FindAirportsByCountryService(countryRepo, unimplementedAirportRepo)
                         .findByCountry(CountryCode("XX"), Pagination(1, 20))
                         .flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        }
      ),
      suite("UpdateAirportService")(
        test("builds the updated airport from the command and returns the repository's result") {
          for
            capturedRef <- Ref.make[Option[Airport]](None)
            repo         = new AirportRepository:
                             def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 =
                               unimplementedAirportRepo.findByIata(iata)
                             def findAll(p: Pagination): IO[DomainError, List[Airport]]                       =
                               unimplementedAirportRepo.findAll(p)
                             def searchByName(q: String): IO[DomainError, List[Airport]]                      =
                               unimplementedAirportRepo.searchByName(q)
                             def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
                               unimplementedAirportRepo.findByCountry(c, p)
                             def save(a: Airport): IO[DomainError, Airport]                                   = unimplementedAirportRepo.save(a)
                             def update(a: Airport): IO[DomainError, Airport]                                 =
                               capturedRef.set(Some(a)).as(a)
                             def delete(iata: IataCode): IO[DomainError, Unit]                                =
                               unimplementedAirportRepo.delete(iata)
            command      = UpdateAirportCommand(IataCode("MAD"), "LEMD", "Madrid-Barajas", "Madrid", CountryCode("ES"))
            result      <- new UpdateAirportService(repo).update(command)
            captured    <- capturedRef.get
          yield assertTrue(
            result == madrid.copy(name = "Madrid-Barajas"),
            captured.contains(madrid.copy(name = "Madrid-Barajas"))
          )
        },
        test("propagates AirportNotFound from the repository") {
          val repo    = new AirportRepository:
            def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]                 = unimplementedAirportRepo.findByIata(iata)
            def findAll(p: Pagination): IO[DomainError, List[Airport]]                       = unimplementedAirportRepo.findAll(p)
            def searchByName(q: String): IO[DomainError, List[Airport]]                      =
              unimplementedAirportRepo.searchByName(q)
            def findByCountry(c: CountryCode, p: Pagination): IO[DomainError, List[Airport]] =
              unimplementedAirportRepo.findByCountry(c, p)
            def save(a: Airport): IO[DomainError, Airport]                                   = unimplementedAirportRepo.save(a)
            def update(a: Airport): IO[DomainError, Airport]                                 = ZIO.fail(DomainError.AirportNotFound("XXX"))
            def delete(iata: IataCode): IO[DomainError, Unit]                                =
              unimplementedAirportRepo.delete(iata)
          val command = UpdateAirportCommand(IataCode("XXX"), "LEMD", "Nowhere", "Nowhere", CountryCode("ES"))
          for error <- new UpdateAirportService(repo).update(command).flip
          yield assertTrue(error == DomainError.AirportNotFound("XXX"))
        }
      ),
      suite("Airport service layers")(
        test("CreateAirportService.layer constructs a usable instance") {
          for svc <- ZIO
                       .service[CreateAirportUseCase]
                       .provide(ZLayer.succeed(unimplementedAirportRepo), CreateAirportService.layer)
          yield assertTrue(svc != null)
        },
        test("FindAirportService.layer constructs a usable instance") {
          for svc <- ZIO
                       .service[FindAirportUseCase]
                       .provide(ZLayer.succeed(unimplementedAirportRepo), FindAirportService.layer)
          yield assertTrue(svc != null)
        },
        test("UpdateAirportService.layer constructs a usable instance") {
          for svc <- ZIO
                       .service[UpdateAirportUseCase]
                       .provide(ZLayer.succeed(unimplementedAirportRepo), UpdateAirportService.layer)
          yield assertTrue(svc != null)
        },
        test("FindAirportsByCountryService.layer constructs a usable instance") {
          for svc <- ZIO
                       .service[FindAirportsByCountryUseCase]
                       .provide(
                         ZLayer.succeed(unimplementedCountryRepo),
                         ZLayer.succeed(unimplementedAirportRepo),
                         FindAirportsByCountryService.layer
                       )
          yield assertTrue(svc != null)
        }
      )
    )
