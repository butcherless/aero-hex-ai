package dev.cmartin.aerohex.adapter.http.airport

import dev.cmartin.aerohex.adapter.http.country.CountryDto
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
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
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AirportRoutes(
    useCase: FindAirportUseCase,
    createSvc: CreateAirportUseCase,
    findByCountrySvc: FindAirportsByCountryUseCase,
    findCountrySvc: FindCountryForAirportUseCase,
    updateSvc: UpdateAirportUseCase,
    deleteSvc: DeleteAirportUseCase
):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AirportEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AirportDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.searchByName.zServerLogic { q =>
      useCase
        .searchByName(q)
        .map(_.map(AirportDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.findByIata.zServerLogic { iata =>
      useCase
        .findByIata(iata)
        .map(AirportDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.findCountry.zServerLogic { iata =>
      findCountrySvc
        .findCountry(IataCode.unsafeMake(iata))
        .map(CountryDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.create.zServerLogic { req =>
      CreateAirportRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { airport =>
          val dto = AirportDto.fromDomain(airport)
          (dto, s"/api/v1/airports/${dto.iata}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.findByCountry.zServerLogic { (code, page, pageSize) =>
      findByCountrySvc
        .findByCountry(CountryCode.unsafeMake(code), Pagination(page, pageSize))
        .map(_.map(AirportDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.update.zServerLogic { (iata, req) =>
      updateSvc
        .update(UpdateAirportRequest.toCommand(iata, req))
        .map(AirportDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.delete.zServerLogic { iata =>
      deleteSvc
        .delete(IataCode.unsafeMake(iata))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AirportRoutes:
  val layer: URLayer[
    FindAirportUseCase & CreateAirportUseCase & FindAirportsByCountryUseCase & FindCountryForAirportUseCase &
      UpdateAirportUseCase & DeleteAirportUseCase,
    AirportRoutes
  ] =
    ZLayer.fromFunction(new AirportRoutes(_, _, _, _, _, _))
