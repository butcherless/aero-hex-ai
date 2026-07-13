package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{DisassociateAirlineUseCase, RouteAirlineRepository}
import zio.{IO, URLayer, ZLayer}

final class DisassociateAirlineService(repo: RouteAirlineRepository) extends DisassociateAirlineUseCase {

  override def disassociate(originIata: String, destinationIata: String, airlineIcao: String): IO[DomainError, Unit] =
    repo.disassociate(
      IataCode.unsafeMake(originIata),
      IataCode.unsafeMake(destinationIata),
      IcaoCode.unsafeMake(airlineIcao)
    ) @@ ServiceAspect.logged(s"DisassociateAirlineService.disassociate($originIata, $destinationIata, $airlineIcao)")
}

object DisassociateAirlineService {
  val layer: URLayer[RouteAirlineRepository, DisassociateAirlineUseCase] =
    ZLayer.fromFunction(new DisassociateAirlineService(_))
}
