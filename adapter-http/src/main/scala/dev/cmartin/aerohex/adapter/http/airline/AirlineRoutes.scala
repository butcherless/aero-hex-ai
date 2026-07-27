package dev.cmartin.aerohex.adapter.http.airline

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.airline.{
  AirlineIcaoCode,
  CreateAirlineUseCase,
  DeleteAirlineUseCase,
  FindAirlineUseCase,
  FindAirlinesByCountryUseCase,
  UpdateAirlineUseCase
}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.route.FindAirlinesByRouteUseCase
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AirlineRoutes(
    useCase: FindAirlineUseCase,
    createSvc: CreateAirlineUseCase,
    findByCountrySvc: FindAirlinesByCountryUseCase,
    updateSvc: UpdateAirlineUseCase,
    deleteSvc: DeleteAirlineUseCase,
    findByRouteSvc: FindAirlinesByRouteUseCase,
    tokenService: TokenService
):
  private val secured = SecuredEndpoint.securityLogic(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AirlineEndpoints.findAll.zServerSecurityLogic(secured).serverLogic { _ => (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AirlineDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.searchByName.zServerSecurityLogic(secured).serverLogic { _ => q =>
      useCase
        .searchByName(q)
        .map(_.map(AirlineDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.findByIcao.zServerSecurityLogic(secured).serverLogic { _ => icao =>
      useCase
        .findByIcao(icao)
        .map(AirlineDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.create.zServerSecurityLogic(secured).serverLogic { _ => req =>
      CreateAirlineRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { airline =>
          val dto = AirlineDto.fromDomain(airline)
          (dto, s"/api/v1/airlines/${dto.icao}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.findByCountry.zServerSecurityLogic(secured).serverLogic { _ => (code, page, pageSize) =>
      findByCountrySvc
        .findByCountry(CountryCode.unsafeMake(code), Pagination(page, pageSize))
        .map(_.map(AirlineDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.update.zServerSecurityLogic(secured).serverLogic { _ => (icao, req) =>
      updateSvc
        .update(UpdateAirlineRequest.toCommand(icao, req))
        .map(AirlineDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.delete.zServerSecurityLogic(secured).serverLogic { _ => icao =>
      deleteSvc
        .delete(AirlineIcaoCode.unsafeMake(icao))
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.findByRoute.zServerSecurityLogic(secured).serverLogic { _ => (origin, destination) =>
      findByRouteSvc
        .findByRoute(origin, destination)
        .map(_.map(AirlineDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AirlineRoutes:
  val layer: URLayer[
    FindAirlineUseCase & CreateAirlineUseCase & FindAirlinesByCountryUseCase & UpdateAirlineUseCase &
      DeleteAirlineUseCase & FindAirlinesByRouteUseCase & TokenService,
    AirlineRoutes
  ] =
    ZLayer.fromFunction(new AirlineRoutes(_, _, _, _, _, _, _))
