package dev.cmartin.aerohex.application.airline

import AirlineRepositoryStub.{stubAirlineRepo, unimplementedAirlineRepo}
import dev.cmartin.aerohex.application.country.CountryRepositoryStub.{stubCountryRepo, unimplementedCountryRepo}
import dev.cmartin.aerohex.domain.airline.{
  Airline,
  AirlineIcaoCode,
  CreateAirlineCommand,
  CreateAirlineUseCase,
  DeleteAirlineUseCase,
  FindAirlineUseCase,
  FindAirlinesByCountryUseCase,
  UpdateAirlineCommand,
  UpdateAirlineUseCase
}
import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import java.time.LocalDate
import zio.test.*
import zio.{Ref, Scope, ZIO, ZLayer}

object AirlineServiceSpec extends ZIOSpecDefault:

  private val iberia  = Airline(AirlineIcaoCode("IBE"), "Iberia", LocalDate.of(1927, 6, 28))
  private val vueling = Airline(AirlineIcaoCode("VLG"), "Vueling", LocalDate.of(2004, 3, 1))
  private val spain   = Country(CountryCode("ES"), "Spain")

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Airline application services")(
      suite("CreateAirlineService")(
        test("saves and returns the new airline when it does not already exist") {
          for
            savedRef <- Ref.make[Option[Airline]](None)
            repo      = stubAirlineRepo(
                          onFindByIcao = _ => ZIO.none,
                          onSave = (a, _) => savedRef.set(Some(a)).as(a)
                        )
            command   =
              CreateAirlineCommand(AirlineIcaoCode("IBE"), "Iberia", LocalDate.of(1927, 6, 28), CountryCode("ES"))
            result   <- new CreateAirlineService(repo).create(command)
            saved    <- savedRef.get
          yield assertTrue(
            result == iberia,
            saved.contains(iberia)
          )
        },
        test("fails with AirlineAlreadyExists and never calls save when the airline already exists") {
          val repo    = stubAirlineRepo(onFindByIcao = _ => ZIO.some(iberia))
          val command =
            CreateAirlineCommand(AirlineIcaoCode("IBE"), "Other name", LocalDate.of(1927, 6, 28), CountryCode("ES"))
          for error <- new CreateAirlineService(repo).create(command).flip
          yield assertTrue(error == DomainError.AirlineAlreadyExists("IBE"))
        }
      ),
      suite("FindAirlineService")(
        test("returns the airline when the repository finds it") {
          val repo = stubAirlineRepo(onFindByIcao = _ => ZIO.some(iberia))
          for result <- new FindAirlineService(repo).findByIcao("IBE")
          yield assertTrue(result == iberia)
        },
        test("fails with AirlineNotFound when the repository has no match") {
          val repo = stubAirlineRepo(onFindByIcao = _ => ZIO.none)
          for error <- new FindAirlineService(repo).findByIcao("XXX").flip
          yield assertTrue(error == DomainError.AirlineNotFound("XXX"))
        },
        test("findAll delegates to the repository unchanged") {
          val repo = stubAirlineRepo(onFindAll = _ => ZIO.succeed(List(iberia, vueling)))
          for result <- new FindAirlineService(repo).findAll(Pagination(1, 20))
          yield assertTrue(result == List(iberia, vueling))
        }
      ),
      suite("FindAirlinesByCountryService")(
        test("returns the airlines in the country when the country exists") {
          val countryRepo = stubCountryRepo(onFindByCode = _ => ZIO.some(spain))
          val airlineRepo = stubAirlineRepo(onFindByCountry = (_, _) => ZIO.succeed(List(iberia)))
          for result <- new FindAirlinesByCountryService(countryRepo, airlineRepo)
                          .findByCountry(CountryCode("ES"), Pagination(1, 20))
          yield assertTrue(result == List(iberia))
        },
        test("fails with CountryNotFound and never queries airlines when the country does not exist") {
          val countryRepo = stubCountryRepo(onFindByCode = _ => ZIO.none)
          for error <- new FindAirlinesByCountryService(countryRepo, unimplementedAirlineRepo)
                         .findByCountry(CountryCode("XX"), Pagination(1, 20))
                         .flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        }
      ),
      suite("UpdateAirlineService")(
        test("builds the updated airline from the command and returns the repository's result") {
          for
            capturedRef <- Ref.make[Option[Airline]](None)
            repo         = stubAirlineRepo(onUpdate = (a, _) => capturedRef.set(Some(a)).as(a))
            command      =
              UpdateAirlineCommand(
                AirlineIcaoCode("IBE"),
                "Iberia Airlines",
                LocalDate.of(1927, 6, 28),
                CountryCode("ES")
              )
            result      <- new UpdateAirlineService(repo).update(command)
            captured    <- capturedRef.get
          yield assertTrue(
            result == iberia.copy(name = "Iberia Airlines"),
            captured.contains(iberia.copy(name = "Iberia Airlines"))
          )
        },
        test("propagates AirlineNotFound from the repository") {
          val repo    = stubAirlineRepo(onUpdate = (_, _) => ZIO.fail(DomainError.AirlineNotFound("XXX")))
          val command =
            UpdateAirlineCommand(AirlineIcaoCode("XXX"), "Nowhere", LocalDate.of(2000, 1, 1), CountryCode("ES"))
          for error <- new UpdateAirlineService(repo).update(command).flip
          yield assertTrue(error == DomainError.AirlineNotFound("XXX"))
        }
      ),
      suite("DeleteAirlineService")(
        test("delegates to the repository and succeeds") {
          val repo = stubAirlineRepo(onDelete = _ => ZIO.unit)
          for result <- new DeleteAirlineService(repo).delete(AirlineIcaoCode("IBE")).exit
          yield assertTrue(result.isSuccess)
        },
        test("propagates AirlineNotFound from the repository") {
          val repo = stubAirlineRepo(onDelete = _ => ZIO.fail(DomainError.AirlineNotFound("XXX")))
          for error <- new DeleteAirlineService(repo).delete(AirlineIcaoCode("XXX")).flip
          yield assertTrue(error == DomainError.AirlineNotFound("XXX"))
        }
      ),
      suite("Airline service layers")(
        test("CreateAirlineService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[CreateAirlineUseCase]
                     .provide(ZLayer.succeed(unimplementedAirlineRepo), CreateAirlineService.layer)
          yield assertCompletes
        },
        test("FindAirlineService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindAirlineUseCase]
                     .provide(ZLayer.succeed(unimplementedAirlineRepo), FindAirlineService.layer)
          yield assertCompletes
        },
        test("UpdateAirlineService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[UpdateAirlineUseCase]
                     .provide(ZLayer.succeed(unimplementedAirlineRepo), UpdateAirlineService.layer)
          yield assertCompletes
        },
        test("FindAirlinesByCountryService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindAirlinesByCountryUseCase]
                     .provide(
                       ZLayer.succeed(unimplementedCountryRepo),
                       ZLayer.succeed(unimplementedAirlineRepo),
                       FindAirlinesByCountryService.layer
                     )
          yield assertCompletes
        },
        test("DeleteAirlineService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[DeleteAirlineUseCase]
                     .provide(ZLayer.succeed(unimplementedAirlineRepo), DeleteAirlineService.layer)
          yield assertCompletes
        }
      )
    )
