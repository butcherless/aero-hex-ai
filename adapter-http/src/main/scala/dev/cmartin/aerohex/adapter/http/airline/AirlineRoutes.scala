package dev.cmartin.aerohex.adapter.http.airline

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
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
import sttp.tapir.ztapir.ZServerEndpoint
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
  private val secured = SecuredEndpoint(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    secured(AirlineEndpoints.findAll) { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AirlineDto.fromDomain))
    },
    secured(AirlineEndpoints.searchByName) { q =>
      useCase
        .searchByName(q)
        .map(_.map(AirlineDto.fromDomain))
    },
    secured(AirlineEndpoints.findByIcao) { icao =>
      useCase
        .findByIcao(icao)
        .map(AirlineDto.fromDomain)
    },
    secured(AirlineEndpoints.create) { req =>
      CreateAirlineRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { airline =>
          val dto = AirlineDto.fromDomain(airline)
          (dto, s"/api/v1/airlines/${dto.icao}")
        }
    },
    secured(AirlineEndpoints.findByCountry) { (code, page, pageSize) =>
      findByCountrySvc
        .findByCountry(CountryCode.unsafeMake(code), Pagination(page, pageSize))
        .map(_.map(AirlineDto.fromDomain))
    },
    secured(AirlineEndpoints.update) { (icao, req) =>
      updateSvc
        .update(UpdateAirlineRequest.toCommand(icao, req))
        .map(AirlineDto.fromDomain)
    },
    secured(AirlineEndpoints.delete) { icao =>
      deleteSvc
        .delete(AirlineIcaoCode.unsafeMake(icao))
    },
    secured(AirlineEndpoints.findByRoute) { (origin, destination) =>
      findByRouteSvc
        .findByRoute(origin, destination)
        .map(_.map(AirlineDto.fromDomain))
    }
  )

object AirlineRoutes:
  val layer: URLayer[
    FindAirlineUseCase & CreateAirlineUseCase & FindAirlinesByCountryUseCase & UpdateAirlineUseCase &
      DeleteAirlineUseCase & FindAirlinesByRouteUseCase & TokenService,
    AirlineRoutes
  ] =
    ZLayer.fromFunction(new AirlineRoutes(_, _, _, _, _, _, _))
