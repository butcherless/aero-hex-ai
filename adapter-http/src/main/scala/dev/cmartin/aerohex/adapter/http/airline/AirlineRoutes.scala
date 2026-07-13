package dev.cmartin.aerohex.adapter.http.airline

import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.airline.{
  CreateAirlineUseCase,
  DeleteAirlineUseCase,
  FindAirlineUseCase,
  FindAirlinesByCountryUseCase,
  IcaoCode,
  UpdateAirlineUseCase
}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.route.FindAirlinesByRouteUseCase
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AirlineRoutes(
    useCase: FindAirlineUseCase,
    createSvc: CreateAirlineUseCase,
    findByCountrySvc: FindAirlinesByCountryUseCase,
    updateSvc: UpdateAirlineUseCase,
    deleteSvc: DeleteAirlineUseCase,
    findByRouteSvc: FindAirlinesByRouteUseCase
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
    AirlineEndpoints.findByCountry.zServerLogic { (code, page, pageSize) =>
      findByCountrySvc
        .findByCountry(CountryCode.unsafeMake(code), Pagination(page, pageSize))
        .map(_.map(AirlineDto.fromDomain))
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
    },
    AirlineEndpoints.findByRoute.zServerLogic { (origin, destination) =>
      findByRouteSvc
        .findByRoute(origin, destination)
        .map(_.map(AirlineDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AirlineRoutes:
  val layer: URLayer[
    FindAirlineUseCase & CreateAirlineUseCase & FindAirlinesByCountryUseCase & UpdateAirlineUseCase &
      DeleteAirlineUseCase & FindAirlinesByRouteUseCase,
    AirlineRoutes
  ] =
    ZLayer.fromFunction(new AirlineRoutes(_, _, _, _, _, _))
