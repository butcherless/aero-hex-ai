package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{IcaoCode, Route, RouteId}
import dev.cmartin.aerohex.domain.port.in.{CreateRouteCommand, CreateRouteUseCase, FindAirportUseCase}
import dev.cmartin.aerohex.domain.port.out.RouteRepository
import dev.cmartin.aerohex.domain.service.RouteValidator
import zio.{IO, ZLayer, URLayer}

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
                       airlineIcao = IcaoCode(command.airlineIcao),
                       distanceKm = command.distanceKm
                     )
      saved       <- routeRepository.save(route)
    } yield saved
}

object CreateRouteService {
  val layer: URLayer[FindAirportUseCase & RouteRepository, CreateRouteUseCase] =
    ZLayer.fromFunction(new CreateRouteService(_, _))
}
