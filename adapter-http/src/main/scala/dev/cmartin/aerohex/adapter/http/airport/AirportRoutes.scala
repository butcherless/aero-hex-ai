package dev.cmartin.aerohex.adapter.http.airport

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.adapter.http.country.CountryDto
import dev.cmartin.aerohex.domain.airport.{
  CreateAirportUseCase,
  DeleteAirportUseCase,
  FindAirportUseCase,
  FindAirportsByCountryUseCase,
  FindCountryForAirportUseCase,
  IataCode,
  UpdateAirportUseCase
}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*

class AirportRoutes(
    useCase: FindAirportUseCase,
    createSvc: CreateAirportUseCase,
    findByCountrySvc: FindAirportsByCountryUseCase,
    findCountrySvc: FindCountryForAirportUseCase,
    updateSvc: UpdateAirportUseCase,
    deleteSvc: DeleteAirportUseCase,
    tokenService: TokenService
):
  private val secured = SecuredEndpoint(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    secured(AirportEndpoints.findAll) { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AirportDto.fromDomain))
    },
    secured(AirportEndpoints.searchByName) { q =>
      useCase
        .searchByName(q)
        .map(_.map(AirportDto.fromDomain))
    },
    secured(AirportEndpoints.findByIata) { iata =>
      useCase
        .findByIata(iata)
        .map(AirportDto.fromDomain)
    },
    secured(AirportEndpoints.findCountry) { iata =>
      findCountrySvc
        .findCountry(IataCode.unsafeMake(iata))
        .map(CountryDto.fromDomain)
    },
    secured(AirportEndpoints.create) { req =>
      CreateAirportRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { airport =>
          val dto = AirportDto.fromDomain(airport)
          (dto, s"/api/v1/airports/${dto.iata}")
        }
    },
    secured(AirportEndpoints.findByCountry) { (code, page, pageSize) =>
      findByCountrySvc
        .findByCountry(CountryCode.unsafeMake(code), Pagination(page, pageSize))
        .map(_.map(AirportDto.fromDomain))
    },
    secured(AirportEndpoints.update) { (iata, req) =>
      updateSvc
        .update(UpdateAirportRequest.toCommand(iata, req))
        .map(AirportDto.fromDomain)
    },
    secured(AirportEndpoints.delete) { iata =>
      deleteSvc
        .delete(IataCode.unsafeMake(iata))
    }
  )

object AirportRoutes:
  val layer: URLayer[
    FindAirportUseCase & CreateAirportUseCase & FindAirportsByCountryUseCase & FindCountryForAirportUseCase &
      UpdateAirportUseCase & DeleteAirportUseCase & TokenService,
    AirportRoutes
  ] =
    ZLayer.fromFunction(new AirportRoutes(_, _, _, _, _, _, _))
