package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.RouteNotFound
import dev.cmartin.aerohex.domain.route.{FindRouteUseCase, Route, RouteRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindRouteService(repo: RouteRepository) extends FindRouteUseCase {

  override def findBySegment(origin: IataCode, destination: IataCode): IO[DomainError, Route] =
    repo.findBySegment(origin, destination).someOrFail(RouteNotFound(origin.value, destination.value)) @@
      ServiceAspect.logged(s"FindRouteService.findBySegment(${origin.value}, ${destination.value})")

  override def findAll(pagination: Pagination): IO[DomainError, List[Route]] =
    repo.findAll(pagination) @@ ServiceAspect.logged("FindRouteService.findAll")

  override def findAllUnbounded: IO[DomainError, List[Route]] =
    repo.findAllUnbounded @@ ServiceAspect.logged("FindRouteService.findAllUnbounded")
}

object FindRouteService {
  val layer: URLayer[RouteRepository, FindRouteUseCase] =
    ZLayer.fromFunction(new FindRouteService(_))
}
