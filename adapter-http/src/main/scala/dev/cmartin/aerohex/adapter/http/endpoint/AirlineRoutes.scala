package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.{AirlineDto, CreateAirlineRequest, UpdateAirlineRequest}
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.model.IcaoCode
import dev.cmartin.aerohex.domain.port.in.{
  CreateAirlineUseCase,
  DeleteAirlineUseCase,
  FindAirlineUseCase,
  UpdateAirlineUseCase
}
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AirlineRoutes(
    useCase: FindAirlineUseCase,
    createSvc: CreateAirlineUseCase,
    updateSvc: UpdateAirlineUseCase,
    deleteSvc: DeleteAirlineUseCase
):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AirlineEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AirlineDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.findByIcao.zServerLogic { icao =>
      useCase
        .findByIcao(icao)
        .map(AirlineDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.create.zServerLogic { req =>
      CreateAirlineRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { airline =>
          val dto = AirlineDto.fromDomain(airline)
          (dto, s"/api/v1/airlines/${dto.icao}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.update.zServerLogic { (icao, req) =>
      updateSvc
        .update(UpdateAirlineRequest.toCommand(icao, req))
        .map(AirlineDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.delete.zServerLogic { icao =>
      deleteSvc
        .delete(IcaoCode.unsafeMake(icao))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AirlineRoutes:
  val layer: URLayer[
    FindAirlineUseCase & CreateAirlineUseCase & UpdateAirlineUseCase & DeleteAirlineUseCase,
    AirlineRoutes
  ] =
    ZLayer.fromFunction(new AirlineRoutes(_, _, _, _))
