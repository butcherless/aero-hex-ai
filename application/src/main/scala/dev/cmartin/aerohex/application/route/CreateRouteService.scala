package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.FindAirportUseCase
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.RouteRepository
import dev.cmartin.aerohex.domain.route.RouteValidator
import dev.cmartin.aerohex.domain.route.{CreateRouteCommand, CreateRouteUseCase}
import dev.cmartin.aerohex.domain.route.{Route, RouteId}
import zio.{IO, URLayer, ZLayer}

final class CreateRouteService(
    findAirport: FindAirportUseCase,
    routeRepository: RouteRepository
) extends CreateRouteUseCase {

  override def create(command: CreateRouteCommand): IO[DomainError, Route] =
    for {
      origin      <- findAirport.findByIata(command.originIata)
      destination <- findAirport.findByIata(command.destinationIata)
      _           <- RouteValidator.validate(origin.iataCode, destination.iataCode, command.distanceKm)
      route        = Route(
                       id = RouteId.generate,
                       origin = origin.iataCode,
                       destination = destination.iataCode,
                       airlineIcao = IcaoCode.unsafeMake(command.airlineIcao),
                       distanceKm = command.distanceKm
                     )
      saved       <- routeRepository.save(route)
    } yield saved
}

object CreateRouteService {
  val layer: URLayer[FindAirportUseCase & RouteRepository, CreateRouteUseCase] =
    ZLayer.fromFunction(new CreateRouteService(_, _))
}
