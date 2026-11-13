package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.adapter.http.airline.AirlineDto
import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
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
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*

class FlightRoutes(
    useCase: FindFlightUseCase,
    createSvc: CreateFlightUseCase,
    updateSvc: UpdateFlightUseCase,
    deleteSvc: DeleteFlightUseCase,
    findByAirlineSvc: FindFlightsByAirlineUseCase,
    findAirlineSvc: FindAirlineForFlightUseCase,
    tokenService: TokenService
):
  private val secured = SecuredEndpoint(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    secured(FlightEndpoints.findAll) { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(FlightDto.fromDomain))
    },
    secured(FlightEndpoints.findByCode) { code =>
      useCase
        .findByCode(code)
        .map(FlightDto.fromDomain)
    },
    secured(FlightEndpoints.create) { req =>
      CreateFlightRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { flight =>
          val dto = FlightDto.fromDomain(flight)
          (dto, s"/api/v1/flights/${dto.code}")
        }
    },
    secured(FlightEndpoints.update) { (code, req) =>
      updateSvc
        .update(UpdateFlightRequest.toCommand(code, req))
        .map(FlightDto.fromDomain)
    },
    secured(FlightEndpoints.delete) { code =>
      deleteSvc
        .delete(FlightCode.unsafeMake(code))
    },
    secured(FlightEndpoints.findByAirline) { (icao, page, pageSize) =>
      findByAirlineSvc
        .findByAirline(AirlineIcaoCode.unsafeMake(icao), Pagination(page, pageSize))
        .map(_.map(FlightDto.fromDomain))
    },
    secured(FlightEndpoints.findAirline) { code =>
      findAirlineSvc
        .findAirline(FlightCode.unsafeMake(code))
        .map(AirlineDto.fromDomain)
    }
  )

object FlightRoutes:
  val layer: URLayer[
    FindFlightUseCase & CreateFlightUseCase & UpdateFlightUseCase & DeleteFlightUseCase &
      FindFlightsByAirlineUseCase & FindAirlineForFlightUseCase & TokenService,
    FlightRoutes
  ] =
    ZLayer.fromFunction(new FlightRoutes(_, _, _, _, _, _, _))
