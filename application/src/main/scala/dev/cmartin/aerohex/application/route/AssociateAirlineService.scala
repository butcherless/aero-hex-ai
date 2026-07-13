package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{AssociateAirlineUseCase, RouteAirlineRepository}
import zio.{IO, URLayer, ZLayer}

final class AssociateAirlineService(repo: RouteAirlineRepository) extends AssociateAirlineUseCase {

  override def associate(originIata: String, destinationIata: String, airlineIcao: String): IO[DomainError, Unit] =
    repo.associate(
      IataCode.unsafeMake(originIata),
      IataCode.unsafeMake(destinationIata),
      AirlineIcaoCode.unsafeMake(airlineIcao)
    ) @@
      ServiceAspect.logged(s"AssociateAirlineService.associate($originIata, $destinationIata, $airlineIcao)")
}

object AssociateAirlineService {
  val layer: URLayer[RouteAirlineRepository, AssociateAirlineUseCase] =
    ZLayer.fromFunction(new AssociateAirlineService(_))
}
