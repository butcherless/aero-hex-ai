package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.{AirportDto, CreateAirportRequest, UpdateAirportRequest}
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.model.CountryCode
import dev.cmartin.aerohex.domain.port.in.{
  CreateAirportUseCase,
  FindAirportUseCase,
  FindAirportsByCountryUseCase,
  UpdateAirportUseCase
}
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AirportRoutes(
    useCase: FindAirportUseCase,
    createSvc: CreateAirportUseCase,
    findByCountrySvc: FindAirportsByCountryUseCase,
    updateSvc: UpdateAirportUseCase
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
    AirportEndpoints.create.zServerLogic { req =>
      createSvc
        .create(CreateAirportRequest.toCommand(req))
        .map { airport =>
          val dto = AirportDto.fromDomain(airport)
          (dto, s"/api/v1/airports/${dto.iata}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.findByCountry.zServerLogic { (code, page, pageSize) =>
      findByCountrySvc
        .findByCountry(CountryCode(code), Pagination(page, pageSize))
        .map(_.map(AirportDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.update.zServerLogic { (iata, req) =>
      updateSvc
        .update(UpdateAirportRequest.toCommand(iata, req))
        .map(AirportDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AirportRoutes:
  val layer: URLayer[
    FindAirportUseCase & CreateAirportUseCase & FindAirportsByCountryUseCase & UpdateAirportUseCase,
    AirportRoutes
  ] =
    ZLayer.fromFunction(new AirportRoutes(_, _, _, _))
