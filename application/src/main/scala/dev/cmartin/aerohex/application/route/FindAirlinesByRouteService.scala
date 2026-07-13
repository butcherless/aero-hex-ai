package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.Airline
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{FindAirlinesByRouteUseCase, RouteAirlineRepository}
import zio.{IO, URLayer, ZLayer}

final class FindAirlinesByRouteService(repo: RouteAirlineRepository) extends FindAirlinesByRouteUseCase {

  override def findByRoute(originIata: String, destinationIata: String): IO[DomainError, List[Airline]] =
    repo.findAirlines(IataCode.unsafeMake(originIata), IataCode.unsafeMake(destinationIata)) @@
      ServiceAspect.logged(s"FindAirlinesByRouteService.findByRoute($originIata, $destinationIata)")
}

object FindAirlinesByRouteService {
  val layer: URLayer[RouteAirlineRepository, FindAirlinesByRouteUseCase] =
    ZLayer.fromFunction(new FindAirlinesByRouteService(_))
}
