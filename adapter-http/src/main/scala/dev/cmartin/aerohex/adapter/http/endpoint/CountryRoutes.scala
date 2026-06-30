package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.{CountryDto, CreateCountryRequest, UpdateCountryRequest}
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.model.CountryCode
import dev.cmartin.aerohex.domain.port.in.*
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.{URLayer, ZLayer}

class CountryRoutes(
    findSvc: FindCountryUseCase,
    createSvc: CreateCountryUseCase,
    updateSvc: UpdateCountryUseCase,
    deleteSvc: DeleteCountryUseCase
):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    CountryEndpoints.findAll.zServerLogic { (page, pageSize) =>
      findSvc
        .findAll(Pagination(page, pageSize))
        .map(_.map(CountryDto.fromDomain))
    },
    CountryEndpoints.searchByName.zServerLogic { q =>
      findSvc
        .searchByName(q)
        .map(_.map(CountryDto.fromDomain))
    },
    CountryEndpoints.findByCode.zServerLogic { code =>
      findSvc
        .findByCode(CountryCode(code))
        .map(CountryDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.create.zServerLogic { req =>
      createSvc
        .create(CreateCountryRequest.toCommand(req))
        .map { country =>
          val dto = CountryDto.fromDomain(country)
          (dto, s"/api/v1/countries/${dto.code}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.update.zServerLogic { (code, req) =>
      updateSvc
        .update(UpdateCountryRequest.toCommand(code, req))
        .map(CountryDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.delete.zServerLogic { code =>
      deleteSvc
        .delete(CountryCode(code))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object CountryRoutes:
  val layer: URLayer[
    FindCountryUseCase & CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase,
    CountryRoutes
  ] =
    ZLayer.fromFunction(new CountryRoutes(_, _, _, _))
