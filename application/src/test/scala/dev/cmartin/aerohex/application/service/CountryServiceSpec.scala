package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.in.{
  CreateCountryCommand,
  CreateCountryUseCase,
  DeleteCountryUseCase,
  FindCountryUseCase,
  UpdateCountryCommand,
  UpdateCountryUseCase
}
import RepositoryStubs.{stubCountryRepo, unimplementedCountryRepo}
import dev.cmartin.aerohex.shared.Pagination
import zio.{Ref, Scope, ZIO, ZLayer}
import zio.test.*

object CountryServiceSpec extends ZIOSpecDefault:

  private val spain   = Country(CountryCode("ES"), "Spain")
  private val germany = Country(CountryCode("DE"), "Germany")

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Country application services")(
      suite("CreateCountryService")(
        test("saves and returns the new country when it does not already exist") {
          for
            savedRef <- Ref.make[Option[Country]](None)
            repo      = stubCountryRepo(
                          onIsValidCode = _ => ZIO.succeed(true),
                          onFindByCode = _ => ZIO.none,
                          onSave = c => savedRef.set(Some(c)).as(c)
                        )
            result   <- new CreateCountryService(repo).create(CreateCountryCommand(CountryCode("ES"), "Spain"))
            saved    <- savedRef.get
          yield assertTrue(
            result == spain,
            saved.contains(spain)
          )
        },
        test("fails with CountryAlreadyExists and never calls save when the country already exists") {
          val repo = stubCountryRepo(onIsValidCode = _ => ZIO.succeed(true), onFindByCode = _ => ZIO.some(spain))
          for error <- new CreateCountryService(repo).create(CreateCountryCommand(CountryCode("ES"), "New Name")).flip
          yield assertTrue(error == DomainError.CountryAlreadyExists("ES"))
        },
        test("fails with InvalidCountryCode and never checks existence when the code isn't a real ISO code") {
          val repo = stubCountryRepo(onIsValidCode = _ => ZIO.succeed(false))
          for error <-
              new CreateCountryService(repo).create(CreateCountryCommand(CountryCode("ZZ"), "Nowhere")).flip
          yield assertTrue(error == DomainError.InvalidCountryCode("ZZ"))
        }
      ),
      suite("FindCountryService")(
        test("returns the country when the repository finds it") {
          val repo = stubCountryRepo(onFindByCode = _ => ZIO.some(spain))
          for result <- new FindCountryService(repo).findByCode(CountryCode("ES"))
          yield assertTrue(result == spain)
        },
        test("fails with CountryNotFound when the repository has no match") {
          val repo = stubCountryRepo(onFindByCode = _ => ZIO.none)
          for error <- new FindCountryService(repo).findByCode(CountryCode("XX")).flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        },
        test("findAll delegates to the repository unchanged") {
          val repo = stubCountryRepo(onFindAll = _ => ZIO.succeed(List(spain, germany)))
          for result <- new FindCountryService(repo).findAll(Pagination(1, 20))
          yield assertTrue(result == List(spain, germany))
        },
        test("searchByName delegates to the repository unchanged") {
          val repo = stubCountryRepo(onSearchByName = _ => ZIO.succeed(List(spain)))
          for result <- new FindCountryService(repo).searchByName("Spa")
          yield assertTrue(result == List(spain))
        }
      ),
      suite("UpdateCountryService")(
        test("builds the updated country from the command and returns the repository's result") {
          for
            capturedRef <- Ref.make[Option[Country]](None)
            repo         = stubCountryRepo(onUpdate = c => capturedRef.set(Some(c)).as(c))
            result      <-
              new UpdateCountryService(repo).update(UpdateCountryCommand(CountryCode("ES"), "Kingdom of Spain"))
            captured    <- capturedRef.get
          yield assertTrue(
            result == Country(CountryCode("ES"), "Kingdom of Spain"),
            captured.contains(Country(CountryCode("ES"), "Kingdom of Spain"))
          )
        },
        test("propagates CountryNotFound from the repository") {
          val repo = stubCountryRepo(onUpdate = _ => ZIO.fail(DomainError.CountryNotFound("XX")))
          for error <- new UpdateCountryService(repo).update(UpdateCountryCommand(CountryCode("XX"), "Nowhere")).flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        }
      ),
      suite("DeleteCountryService")(
        test("delegates to the repository and succeeds") {
          val repo = stubCountryRepo(onDelete = _ => ZIO.unit)
          for result <- new DeleteCountryService(repo).delete(CountryCode("ES")).exit
          yield assertTrue(result.isSuccess)
        },
        test("propagates CountryNotFound from the repository") {
          val repo = stubCountryRepo(onDelete = _ => ZIO.fail(DomainError.CountryNotFound("XX")))
          for error <- new DeleteCountryService(repo).delete(CountryCode("XX")).flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        }
      ),
      suite("Country service layers")(
        test("CreateCountryService.layer constructs a usable instance") {
          for _ <-
              ZIO.service[CreateCountryUseCase].provide(
                ZLayer.succeed(unimplementedCountryRepo),
                CreateCountryService.layer
              )
          yield assertCompletes
        },
        test("FindCountryService.layer constructs a usable instance") {
          for _ <-
              ZIO.service[FindCountryUseCase].provide(
                ZLayer.succeed(unimplementedCountryRepo),
                FindCountryService.layer
              )
          yield assertCompletes
        },
        test("UpdateCountryService.layer constructs a usable instance") {
          for _ <-
              ZIO.service[UpdateCountryUseCase].provide(
                ZLayer.succeed(unimplementedCountryRepo),
                UpdateCountryService.layer
              )
          yield assertCompletes
        },
        test("DeleteCountryService.layer constructs a usable instance") {
          for _ <-
              ZIO.service[DeleteCountryUseCase].provide(
                ZLayer.succeed(unimplementedCountryRepo),
                DeleteCountryService.layer
              )
          yield assertCompletes
        }
      )
    )
