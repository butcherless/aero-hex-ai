package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.adapter.http.airline.AirlineDto
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.flight.{
  CreateFlightUseCase,
  DeleteFlightUseCase,
  FindAirlineForFlightUseCase,
  FindFlightUseCase,
  FindFlightsByAirlineUseCase,
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
    deleteSvc: DeleteFlightUseCase,
    findByAirlineSvc: FindFlightsByAirlineUseCase,
    findAirlineSvc: FindAirlineForFlightUseCase
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
    },
    FlightEndpoints.findByAirline.zServerLogic { (icao, page, pageSize) =>
      findByAirlineSvc
        .findByAirline(AirlineIcaoCode.unsafeMake(icao), Pagination(page, pageSize))
        .map(_.map(FlightDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    FlightEndpoints.findAirline.zServerLogic { code =>
      findAirlineSvc
        .findAirline(FlightCode.unsafeMake(code))
        .map(AirlineDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object FlightRoutes:
  val layer: URLayer[
    FindFlightUseCase & CreateFlightUseCase & UpdateFlightUseCase & DeleteFlightUseCase &
      FindFlightsByAirlineUseCase & FindAirlineForFlightUseCase,
    FlightRoutes
  ] =
    ZLayer.fromFunction(new FlightRoutes(_, _, _, _, _, _))
