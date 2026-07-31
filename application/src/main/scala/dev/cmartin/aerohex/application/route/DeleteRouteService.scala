package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{DeleteRouteUseCase, RouteRepository}
import zio.{IO, URLayer, ZLayer}

final class DeleteRouteService(repo: RouteRepository) extends DeleteRouteUseCase {

  override def delete(origin: IataCode, destination: IataCode): IO[DomainError, Unit] =
    repo.delete(origin, destination) @@
      ServiceAspect.logged(s"DeleteRouteService.delete(${origin.value}, ${destination.value})")
}

object DeleteRouteService {
  val layer: URLayer[RouteRepository, DeleteRouteUseCase] =
    ZLayer.fromFunction(new DeleteRouteService(_))
}
