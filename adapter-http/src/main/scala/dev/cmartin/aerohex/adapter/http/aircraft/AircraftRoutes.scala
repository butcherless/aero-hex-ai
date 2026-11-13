package dev.cmartin.aerohex.adapter.http.aircraft

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.domain.aircraft.{
  CreateAircraftUseCase,
  DeleteAircraftUseCase,
  FindAircraftByAirlineUseCase,
  FindAircraftUseCase,
  Registration,
  UpdateAircraftUseCase
}
import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*

class AircraftRoutes(
    useCase: FindAircraftUseCase,
    createSvc: CreateAircraftUseCase,
    updateSvc: UpdateAircraftUseCase,
    deleteSvc: DeleteAircraftUseCase,
    findByAirlineSvc: FindAircraftByAirlineUseCase,
    tokenService: TokenService
):
  private val secured = SecuredEndpoint(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    secured(AircraftEndpoints.findAll) { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AircraftDto.fromDomain))
    },
    secured(AircraftEndpoints.findByRegistration) { registration =>
      useCase
        .findByRegistration(registration)
        .map(AircraftDto.fromDomain)
    },
    secured(AircraftEndpoints.findByAirline) { (icao, page, pageSize) =>
      findByAirlineSvc
        .findByAirline(AirlineIcaoCode.unsafeMake(icao), Pagination(page, pageSize))
        .map(_.map(AircraftDto.fromDomain))
    },
    secured(AircraftEndpoints.create) { req =>
      CreateAircraftRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { aircraft =>
          val dto = AircraftDto.fromDomain(aircraft)
          (dto, s"/api/v1/aircraft/${dto.registration}")
        }
    },
    secured(AircraftEndpoints.update) { (registration, req) =>
      updateSvc
        .update(UpdateAircraftRequest.toCommand(registration, req))
        .map(AircraftDto.fromDomain)
    },
    secured(AircraftEndpoints.delete) { registration =>
      deleteSvc
        .delete(Registration.unsafeMake(registration))
    }
  )

object AircraftRoutes:
  val layer: URLayer[
    FindAircraftUseCase & CreateAircraftUseCase & UpdateAircraftUseCase & DeleteAircraftUseCase &
      FindAircraftByAirlineUseCase & TokenService,
    AircraftRoutes
  ] =
    ZLayer.fromFunction(new AircraftRoutes(_, _, _, _, _, _))
