package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.{AircraftDto, CreateAircraftRequest, UpdateAircraftRequest}
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.model.Registration
import dev.cmartin.aerohex.domain.port.in.{
  CreateAircraftUseCase,
  DeleteAircraftUseCase,
  FindAircraftUseCase,
  UpdateAircraftUseCase
}
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AircraftRoutes(
    useCase: FindAircraftUseCase,
    createSvc: CreateAircraftUseCase,
    updateSvc: UpdateAircraftUseCase,
    deleteSvc: DeleteAircraftUseCase
):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AircraftEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AircraftDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.findByRegistration.zServerLogic { registration =>
      useCase
        .findByRegistration(registration)
        .map(AircraftDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.create.zServerLogic { req =>
      CreateAircraftRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { aircraft =>
          val dto = AircraftDto.fromDomain(aircraft)
          (dto, s"/api/v1/aircraft/${dto.registration}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.update.zServerLogic { (registration, req) =>
      updateSvc
        .update(UpdateAircraftRequest.toCommand(registration, req))
        .map(AircraftDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.delete.zServerLogic { registration =>
      deleteSvc
        .delete(Registration.unsafeMake(registration))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AircraftRoutes:
  val layer: URLayer[
    FindAircraftUseCase & CreateAircraftUseCase & UpdateAircraftUseCase & DeleteAircraftUseCase,
    AircraftRoutes
  ] =
    ZLayer.fromFunction(new AircraftRoutes(_, _, _, _))
