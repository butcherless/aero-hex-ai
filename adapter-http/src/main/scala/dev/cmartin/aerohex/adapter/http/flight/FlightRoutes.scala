package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.flight.{
  CreateFlightUseCase,
  DeleteFlightUseCase,
  FindFlightUseCase,
  FlightCode,
  UpdateFlightUseCase
}
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class FlightRoutes(
    useCase: FindFlightUseCase,
    createSvc: CreateFlightUseCase,
    updateSvc: UpdateFlightUseCase,
    deleteSvc: DeleteFlightUseCase
):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    FlightEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(FlightDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    FlightEndpoints.findByCode.zServerLogic { code =>
      useCase
        .findByCode(code)
        .map(FlightDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    FlightEndpoints.create.zServerLogic { req =>
      CreateFlightRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { flight =>
          val dto = FlightDto.fromDomain(flight)
          (dto, s"/api/v1/flights/${dto.code}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    FlightEndpoints.update.zServerLogic { (code, req) =>
      updateSvc
        .update(UpdateFlightRequest.toCommand(code, req))
        .map(FlightDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    FlightEndpoints.delete.zServerLogic { code =>
      deleteSvc
        .delete(FlightCode.unsafeMake(code))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object FlightRoutes:
  val layer: URLayer[
    FindFlightUseCase & CreateFlightUseCase & UpdateFlightUseCase & DeleteFlightUseCase,
    FlightRoutes
  ] =
    ZLayer.fromFunction(new FlightRoutes(_, _, _, _))
