package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.in.{CreateCountryCommand, UpdateCountryCommand}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, Ref, Scope, UIO, ZIO}
import zio.test.*

object CountryServiceSpec extends ZIOSpecDefault:

  private val spain   = Country(CountryCode("ES"), "Spain")
  private val germany = Country(CountryCode("DE"), "Germany")

  // A repository stub where every method dies unless overridden — any call the test
  // doesn't expect surfaces as a loud failure instead of a silently wrong default.
  private val unimplemented: CountryRepository = new CountryRepository:
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
    suite("Country application services")(
      suite("CreateCountryService")(
        test("saves and returns the new country when it does not already exist") {
          for
            savedRef <- Ref.make[Option[Country]](None)
            repo      = new CountryRepository:
                          def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.none
                          def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
                          def searchByName(q: String): UIO[List[Country]]                     =
                            unimplemented.searchByName(q)
                          def save(c: Country): IO[DomainError, Country]                      =
                            savedRef.set(Some(c)).as(c)
                          def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
                          def delete(code: CountryCode): IO[DomainError, Unit]                =
                            unimplemented.delete(code)
            result   <- new CreateCountryService(repo).create(CreateCountryCommand(CountryCode("ES"), "Spain"))
            saved    <- savedRef.get
          yield assertTrue(
            result == spain,
            saved.contains(spain)
          )
        },
        test("fails with CountryAlreadyExists and never calls save when the country already exists") {
          val repo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.some(spain)
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                = unimplemented.delete(code)
          for error <- new CreateCountryService(repo).create(CreateCountryCommand(CountryCode("ES"), "New Name")).flip
          yield assertTrue(error == DomainError.CountryAlreadyExists("ES"))
        }
      ),
      suite("FindCountryService")(
        test("returns the country when the repository finds it") {
          val repo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.some(spain)
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                = unimplemented.delete(code)
          for result <- new FindCountryService(repo).findByCode(CountryCode("ES"))
          yield assertTrue(result == spain)
        },
        test("fails with CountryNotFound when the repository has no match") {
          val repo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.none
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                = unimplemented.delete(code)
          for error <- new FindCountryService(repo).findByCode(CountryCode("XX")).flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        },
        test("findAll delegates to the repository unchanged") {
          val repo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = unimplemented.findByCode(code)
            def findAll(p: Pagination): UIO[List[Country]]                      = ZIO.succeed(List(spain, germany))
            def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                = unimplemented.delete(code)
          for result <- new FindCountryService(repo).findAll(Pagination(1, 20))
          yield assertTrue(result == List(spain, germany))
        },
        test("searchByName delegates to the repository unchanged") {
          val repo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = unimplemented.findByCode(code)
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     = ZIO.succeed(List(spain))
            def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                = unimplemented.delete(code)
          for result <- new FindCountryService(repo).searchByName("Spa")
          yield assertTrue(result == List(spain))
        }
      ),
      suite("UpdateCountryService")(
        test("builds the updated country from the command and returns the repository's result") {
          for
            capturedRef <- Ref.make[Option[Country]](None)
            repo         = new CountryRepository:
                             def findByCode(code: CountryCode): IO[DomainError, Option[Country]] =
                               unimplemented.findByCode(code)
                             def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
                             def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
                             def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
                             def update(c: Country): IO[DomainError, Country]                    =
                               capturedRef.set(Some(c)).as(c)
                             def delete(code: CountryCode): IO[DomainError, Unit]                = unimplemented.delete(code)
            result      <- new UpdateCountryService(repo).update(UpdateCountryCommand(CountryCode("ES"), "Kingdom of Spain"))
            captured    <- capturedRef.get
          yield assertTrue(
            result == Country(CountryCode("ES"), "Kingdom of Spain"),
            captured.contains(Country(CountryCode("ES"), "Kingdom of Spain"))
          )
        },
        test("propagates CountryNotFound from the repository") {
          val repo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = unimplemented.findByCode(code)
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
            def update(c: Country): IO[DomainError, Country]                    = ZIO.fail(DomainError.CountryNotFound("XX"))
            def delete(code: CountryCode): IO[DomainError, Unit]                = unimplemented.delete(code)
          for error <- new UpdateCountryService(repo).update(UpdateCountryCommand(CountryCode("XX"), "Nowhere")).flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        }
      ),
      suite("DeleteCountryService")(
        test("delegates to the repository and succeeds") {
          val repo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = unimplemented.findByCode(code)
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                = ZIO.unit
          for result <- new DeleteCountryService(repo).delete(CountryCode("ES")).exit
          yield assertTrue(result.isSuccess)
        },
        test("propagates CountryNotFound from the repository") {
          val repo = new CountryRepository:
            def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = unimplemented.findByCode(code)
            def findAll(p: Pagination): UIO[List[Country]]                      = unimplemented.findAll(p)
            def searchByName(q: String): UIO[List[Country]]                     = unimplemented.searchByName(q)
            def save(c: Country): IO[DomainError, Country]                      = unimplemented.save(c)
            def update(c: Country): IO[DomainError, Country]                    = unimplemented.update(c)
            def delete(code: CountryCode): IO[DomainError, Unit]                =
              ZIO.fail(DomainError.CountryNotFound("XX"))
          for error <- new DeleteCountryService(repo).delete(CountryCode("XX")).flip
          yield assertTrue(error == DomainError.CountryNotFound("XX"))
        }
      )
    )
