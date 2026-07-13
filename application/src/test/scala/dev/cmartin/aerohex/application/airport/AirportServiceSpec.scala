package dev.cmartin.aerohex.application.airport

import AirportRepositoryStub.{stubAirportRepo, unimplementedAirportRepo}
import dev.cmartin.aerohex.application.country.CountryRepositoryStub.{stubCountryRepo, unimplementedCountryRepo}
import dev.cmartin.aerohex.domain.airport.{
  Airport,
  AirportIcaoCode,
  CreateAirportCommand,
  CreateAirportUseCase,
  DeleteAirportUseCase,
  FindAirportUseCase,
  FindAirportsByCountryUseCase,
  FindCountryForAirportUseCase,
  IataCode,
  UpdateAirportCommand,
  UpdateAirportUseCase
}
import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.test.*
import zio.{Ref, Scope, ZIO, ZLayer}

object AirportServiceSpec extends ZIOSpecDefault:

  private val madrid    = Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Adolfo Suárez Madrid-Barajas", "Madrid")
  private val barcelona =
    Airport(IataCode("BCN"), AirportIcaoCode("LEBL"), "Josep Tarradellas Barcelona-El Prat", "Barcelona")
  private val spain     = Country(CountryCode("ES"), "Spain")

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Airport application services")(
      suite("CreateAirportService")(
        test("saves and returns the new airport when it does not already exist") {
          for
            savedRef <- Ref.make[Option[Airport]](None)
            repo      = stubAirportRepo(
                          onFindByIata = _ => ZIO.none,
                          onSave = (a, _) => savedRef.set(Some(a)).as(a)
                        )
            command   =
              CreateAirportCommand(
                IataCode("MAD"),
                AirportIcaoCode("LEMD"),
                "Adolfo Suárez Madrid-Barajas",
                "Madrid",
                CountryCode("ES")
              )
            result   <- new CreateAirportService(repo).create(command)
            saved    <- savedRef.get
          yield assertTrue(
            result == madrid,
            saved.contains(madrid)
          )
        },
        test("fails with AirportAlreadyExists and never calls save when the airport already exists") {
          val repo    = stubAirportRepo(onFindByIata = _ => ZIO.some(madrid))
          val command =
            CreateAirportCommand(IataCode("MAD"), AirportIcaoCode("LEMD"), "Other name", "Madrid", CountryCode("ES"))
          for error <- new CreateAirportService(repo).create(command).flip
          yield assertTrue(error == DomainError.AirportAlreadyExists("MAD"))
        }
      ),
      suite("FindAirportService")(
        test("returns the airport when the repository finds it") {
          val repo = stubAirportRepo(onFindByIata = _ => ZIO.some(madrid))
          for result <- new FindAirportService(repo).findByIata("MAD")
          yield assertTrue(result == madrid)
        },
        test("fails with AirportNotFound when the repository has no match") {
          val repo = stubAirportRepo(onFindByIata = _ => ZIO.none)
          for error <- new FindAirportService(repo).findByIata("XXX").flip
          yield assertTrue(error == DomainError.AirportNotFound("XXX"))
        },
        test("findAll delegates to the repository unchanged") {
          val repo = stubAirportRepo(onFindAll = _ => ZIO.succeed(List(madrid, barcelona)))
          for result <- new FindAirportService(repo).findAll(Pagination(1, 20))
          yield assertTrue(result == List(madrid, barcelona))
        },
        test("searchByName delegates to the repository unchanged") {
          val repo = stubAirportRepo(onSearchByName = _ => ZIO.succeed(List(madrid)))
          for result <- new FindAirportService(repo).searchByName("Madrid")
          yield assertTrue(result == List(madrid))
        }
      ),
      suite("FindAirportsByCountryService")(
        test("returns the airports in the country when the country exists") {
          val countryRepo = stubCountryRepo(onFindByCode = _ => ZIO.some(spain))
          val airportRepo = stubAirportRepo(onFindByCountry = (_, _) => ZIO.succeed(List(madrid)))
          for result <- new FindAirportsByCountryService(countryRepo, airportRepo)
                          .findByCountry(CountryCode("ES"), Pagination(1, 20))
          yield assertTrue(result == List(madrid))
        },
        test("fails with CountryNotFound and never queries airports when the country does not exist") {
          val countryRepo = stubCountryRepo(onFindByCode = _ => ZIO.none)
          for error <- new FindAirportsByCountryService(countryRepo, unimplementedAirportRepo)
                         .findByCountry(CountryCode("XX"), Pagination(1, 20))
                         .flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        }
      ),
      suite("FindCountryForAirportService")(
        test("returns the airport's country when the airport exists") {
          val repo = stubAirportRepo(onFindCountryByIata = _ => ZIO.some(spain))
          for result <- new FindCountryForAirportService(repo).findCountry(IataCode("MAD"))
          yield assertTrue(result == spain)
        },
        test("fails with AirportNotFound when the airport does not exist") {
          val repo = stubAirportRepo(onFindCountryByIata = _ => ZIO.none)
          for error <- new FindCountryForAirportService(repo).findCountry(IataCode("XXX")).flip
          yield assertTrue(error == DomainError.AirportNotFound("XXX"))
        }
      ),
      suite("UpdateAirportService")(
        test("builds the updated airport from the command and returns the repository's result") {
          for
            capturedRef <- Ref.make[Option[Airport]](None)
            repo         = stubAirportRepo(onUpdate = (a, _) => capturedRef.set(Some(a)).as(a))
            command      =
              UpdateAirportCommand(
                IataCode("MAD"),
                AirportIcaoCode("LEMD"),
                "Madrid-Barajas",
                "Madrid",
                CountryCode("ES")
              )
            result      <- new UpdateAirportService(repo).update(command)
            captured    <- capturedRef.get
          yield assertTrue(
            result == madrid.copy(name = "Madrid-Barajas"),
            captured.contains(madrid.copy(name = "Madrid-Barajas"))
          )
        },
        test("propagates AirportNotFound from the repository") {
          val repo    = stubAirportRepo(onUpdate = (_, _) => ZIO.fail(DomainError.AirportNotFound("XXX")))
          val command =
            UpdateAirportCommand(IataCode("XXX"), AirportIcaoCode("LEMD"), "Nowhere", "Nowhere", CountryCode("ES"))
          for error <- new UpdateAirportService(repo).update(command).flip
          yield assertTrue(error == DomainError.AirportNotFound("XXX"))
        }
      ),
      suite("DeleteAirportService")(
        test("delegates to the repository and succeeds") {
          val repo = stubAirportRepo(onDelete = _ => ZIO.unit)
          for result <- new DeleteAirportService(repo).delete(IataCode("MAD")).exit
          yield assertTrue(result.isSuccess)
        },
        test("propagates AirportNotFound from the repository") {
          val repo = stubAirportRepo(onDelete = _ => ZIO.fail(DomainError.AirportNotFound("XXX")))
          for error <- new DeleteAirportService(repo).delete(IataCode("XXX")).flip
          yield assertTrue(error == DomainError.AirportNotFound("XXX"))
        }
      ),
      suite("Airport service layers")(
        test("CreateAirportService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[CreateAirportUseCase]
                     .provide(ZLayer.succeed(unimplementedAirportRepo), CreateAirportService.layer)
          yield assertCompletes
        },
        test("FindAirportService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindAirportUseCase]
                     .provide(ZLayer.succeed(unimplementedAirportRepo), FindAirportService.layer)
          yield assertCompletes
        },
        test("UpdateAirportService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[UpdateAirportUseCase]
                     .provide(ZLayer.succeed(unimplementedAirportRepo), UpdateAirportService.layer)
          yield assertCompletes
        },
        test("FindAirportsByCountryService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindAirportsByCountryUseCase]
                     .provide(
                       ZLayer.succeed(unimplementedCountryRepo),
                       ZLayer.succeed(unimplementedAirportRepo),
                       FindAirportsByCountryService.layer
                     )
          yield assertCompletes
        },
        test("DeleteAirportService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[DeleteAirportUseCase]
                     .provide(ZLayer.succeed(unimplementedAirportRepo), DeleteAirportService.layer)
          yield assertCompletes
        },
        test("FindCountryForAirportService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindCountryForAirportUseCase]
                     .provide(ZLayer.succeed(unimplementedAirportRepo), FindCountryForAirportService.layer)
          yield assertCompletes
        }
      )
    )
