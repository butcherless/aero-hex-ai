package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{FindRoutesByAirlineUseCase, Route, RouteAirlineRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindRoutesByAirlineService(repo: RouteAirlineRepository) extends FindRoutesByAirlineUseCase {

  override def findByAirline(airlineIcao: String, pagination: Pagination): IO[DomainError, List[Route]] =
    repo.findRoutes(IcaoCode.unsafeMake(airlineIcao), pagination) @@
      ServiceAspect.logged(s"FindRoutesByAirlineService.findByAirline($airlineIcao)")
}

object FindRoutesByAirlineService {
  val layer: URLayer[RouteAirlineRepository, FindRoutesByAirlineUseCase] =
    ZLayer.fromFunction(new FindRoutesByAirlineService(_))
}
