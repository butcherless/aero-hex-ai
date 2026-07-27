package dev.cmartin.aerohex.adapter.http.aircraft

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.aircraft.{
  CreateAircraftUseCase,
  DeleteAircraftUseCase,
  FindAircraftUseCase,
  Registration,
  UpdateAircraftUseCase
}
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AircraftRoutes(
    useCase: FindAircraftUseCase,
    createSvc: CreateAircraftUseCase,
    updateSvc: UpdateAircraftUseCase,
    deleteSvc: DeleteAircraftUseCase,
    tokenService: TokenService
):
  private val secured = SecuredEndpoint.securityLogic(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AircraftEndpoints.findAll.zServerSecurityLogic(secured).serverLogic { _ => (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AircraftDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.findByRegistration.zServerSecurityLogic(secured).serverLogic { _ => registration =>
      useCase
        .findByRegistration(registration)
        .map(AircraftDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.create.zServerSecurityLogic(secured).serverLogic { _ => req =>
      CreateAircraftRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { aircraft =>
          val dto = AircraftDto.fromDomain(aircraft)
          (dto, s"/api/v1/aircraft/${dto.registration}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.update.zServerSecurityLogic(secured).serverLogic { _ => (registration, req) =>
      updateSvc
        .update(UpdateAircraftRequest.toCommand(registration, req))
        .map(AircraftDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.delete.zServerSecurityLogic(secured).serverLogic { _ => registration =>
      deleteSvc
        .delete(Registration.unsafeMake(registration))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AircraftRoutes:
  val layer: URLayer[
    FindAircraftUseCase & CreateAircraftUseCase & UpdateAircraftUseCase & DeleteAircraftUseCase & TokenService,
    AircraftRoutes
  ] =
    ZLayer.fromFunction(new AircraftRoutes(_, _, _, _, _))
