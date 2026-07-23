package dev.cmartin.aerohex.application.route

import RouteAirlineRepositoryStub.{stubRouteAirlineRepo, unimplementedRouteAirlineRepo}
import RouteRepositoryStub.{stubRouteRepo, unimplementedRouteRepo}
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.airport.{Airport, AirportIcaoCode, FindAirportUseCase, IataCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{
  AssociateAirlineUseCase,
  CreateRouteCommand,
  CreateRouteUseCase,
  DisassociateAirlineUseCase,
  FindAirlinesByRouteUseCase,
  FindRoutesByAirlineUseCase,
  Route
}
import dev.cmartin.aerohex.shared.Pagination
import zio.test.*
import zio.{IO, Scope, ZIO, ZLayer}

object RouteServiceSpec extends ZIOSpecDefault:

  private val mad    = Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Barajas", "Madrid")
  private val tfn    = Airport(IataCode("TFN"), AirportIcaoCode("GCXO"), "Norte", "Tenerife")
  private val route  = Route(IataCode("MAD"), IataCode("TFN"), 1740)
  private val iberia = Airline(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA"))

  private def findAirportStub(byIata: String => IO[DomainError, Airport]): FindAirportUseCase =
    new FindAirportUseCase:
      def findByIata(iata: String): IO[DomainError, Airport]                         = byIata(iata)
      def findAll(p: Pagination): IO[DomainError, List[Airport]]                     = ZIO.die(new NotImplementedError("findAll"))
      def findAllUnbounded: IO[DomainError, List[Airport]]                           =
        ZIO.die(new NotImplementedError("findAllUnbounded"))
      def findAllUnboundedWithCountry: IO[DomainError, List[(Airport, CountryCode)]] =
        ZIO.die(new NotImplementedError("findAllUnboundedWithCountry"))
      def searchByName(query: String): IO[DomainError, List[Airport]]                =
        ZIO.die(new NotImplementedError("searchByName"))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Route application services")(
      suite("CreateRouteService")(
        test("saves and returns the new route when it does not already exist") {
          val findAirport = findAirportStub {
            case "MAD" => ZIO.succeed(mad)
            case "TFN" => ZIO.succeed(tfn)
          }
          val repo        = stubRouteRepo(onFindBySegment = (_, _) => ZIO.none, onSave = r => ZIO.succeed(r))
          for result <-
              new CreateRouteService(findAirport, repo).create(CreateRouteCommand("MAD", "TFN", 1740))
          yield assertTrue(result == route)
        },
        test("fails with RouteAlreadyExists and never calls save when the segment already exists") {
          val findAirport = findAirportStub {
            case "MAD" => ZIO.succeed(mad)
            case "TFN" => ZIO.succeed(tfn)
          }
          val repo        = stubRouteRepo(onFindBySegment = (_, _) => ZIO.some(route))
          for error <-
              new CreateRouteService(findAirport, repo).create(CreateRouteCommand("MAD", "TFN", 1740)).flip
          yield assertTrue(error == DomainError.RouteAlreadyExists("MAD", "TFN"))
        },
        test("propagates AirportNotFound from the origin lookup") {
          val findAirport = findAirportStub(_ => ZIO.fail(DomainError.AirportNotFound("XXX")))
          for error <-
              new CreateRouteService(findAirport, unimplementedRouteRepo)
                .create(CreateRouteCommand("XXX", "TFN", 1740))
                .flip
          yield assertTrue(error == DomainError.AirportNotFound("XXX"))
        },
        test("fails with InvalidRoute when origin and destination are the same airport") {
          val findAirport = findAirportStub(_ => ZIO.succeed(mad))
          for error <-
              new CreateRouteService(findAirport, unimplementedRouteRepo)
                .create(CreateRouteCommand("MAD", "MAD", 1740))
                .flip
          yield assertTrue(error.isInstanceOf[DomainError.InvalidRoute])
        }
      ),
      suite("AssociateAirlineService")(
        test("delegates to the repository and succeeds") {
          val repo = stubRouteAirlineRepo(onAssociate = (_, _, _) => ZIO.unit)
          for result <- new AssociateAirlineService(repo).associate("MAD", "TFN", "IBE").exit
          yield assertTrue(result.isSuccess)
        },
        test("propagates RouteAirlineAlreadyExists from the repository") {
          val repo = stubRouteAirlineRepo(onAssociate =
            (_, _, _) => ZIO.fail(DomainError.RouteAirlineAlreadyExists("MAD", "TFN", "IBE"))
          )
          for error <- new AssociateAirlineService(repo).associate("MAD", "TFN", "IBE").flip
          yield assertTrue(error == DomainError.RouteAirlineAlreadyExists("MAD", "TFN", "IBE"))
        }
      ),
      suite("DisassociateAirlineService")(
        test("delegates to the repository and succeeds") {
          val repo = stubRouteAirlineRepo(onDisassociate = (_, _, _) => ZIO.unit)
          for result <- new DisassociateAirlineService(repo).disassociate("MAD", "TFN", "IBE").exit
          yield assertTrue(result.isSuccess)
        },
        test("propagates RouteAirlineNotFound from the repository") {
          val repo = stubRouteAirlineRepo(onDisassociate =
            (_, _, _) => ZIO.fail(DomainError.RouteAirlineNotFound("MAD", "TFN", "IBE"))
          )
          for error <- new DisassociateAirlineService(repo).disassociate("MAD", "TFN", "IBE").flip
          yield assertTrue(error == DomainError.RouteAirlineNotFound("MAD", "TFN", "IBE"))
        }
      ),
      suite("FindAirlinesByRouteService")(
        test("returns the airlines operating the route") {
          val repo = stubRouteAirlineRepo(onFindAirlines = (_, _) => ZIO.succeed(List(iberia)))
          for result <- new FindAirlinesByRouteService(repo).findByRoute("MAD", "TFN")
          yield assertTrue(result == List(iberia))
        }
      ),
      suite("FindRoutesByAirlineService")(
        test("returns the routes operated by the airline") {
          val repo = stubRouteAirlineRepo(onFindRoutes = (_, _) => ZIO.succeed(List(route)))
          for result <- new FindRoutesByAirlineService(repo).findByAirline("IBE", Pagination(1, 20))
          yield assertTrue(result == List(route))
        }
      ),
      suite("Route service layers")(
        test("CreateRouteService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[CreateRouteUseCase]
                     .provide(
                       ZLayer.succeed(findAirportStub(_ => ZIO.die(new NotImplementedError))),
                       ZLayer.succeed(unimplementedRouteRepo),
                       CreateRouteService.layer
                     )
          yield assertCompletes
        },
        test("AssociateAirlineService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[AssociateAirlineUseCase]
                     .provide(ZLayer.succeed(unimplementedRouteAirlineRepo), AssociateAirlineService.layer)
          yield assertCompletes
        },
        test("DisassociateAirlineService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[DisassociateAirlineUseCase]
                     .provide(ZLayer.succeed(unimplementedRouteAirlineRepo), DisassociateAirlineService.layer)
          yield assertCompletes
        },
        test("FindAirlinesByRouteService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindAirlinesByRouteUseCase]
                     .provide(ZLayer.succeed(unimplementedRouteAirlineRepo), FindAirlinesByRouteService.layer)
          yield assertCompletes
        },
        test("FindRoutesByAirlineService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindRoutesByAirlineUseCase]
                     .provide(ZLayer.succeed(unimplementedRouteAirlineRepo), FindRoutesByAirlineService.layer)
          yield assertCompletes
        }
      )
    )
