package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.domain.airport.FindAirportUseCase
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.Route
import dev.cmartin.aerohex.domain.route.RouteRepository
import dev.cmartin.aerohex.domain.route.RouteValidator
import dev.cmartin.aerohex.domain.route.{UpdateRouteCommand, UpdateRouteUseCase}
import zio.{IO, URLayer, ZLayer}

final class UpdateRouteService(
    findAirport: FindAirportUseCase,
    routeRepository: RouteRepository
) extends UpdateRouteUseCase {

  override def update(command: UpdateRouteCommand): IO[DomainError, Route] =
    for {
      origin      <- findAirport.findByIata(command.originIata)
      destination <- findAirport.findByIata(command.destinationIata)
      _           <- RouteValidator.validate(origin.iataCode, destination.iataCode, command.distanceKm)
      route        = Route(origin.iataCode, destination.iataCode, command.distanceKm)
      saved       <- routeRepository.update(route)
    } yield saved
}

object UpdateRouteService {
  val layer: URLayer[FindAirportUseCase & RouteRepository, UpdateRouteUseCase] =
    ZLayer.fromFunction(new UpdateRouteService(_, _))
}
