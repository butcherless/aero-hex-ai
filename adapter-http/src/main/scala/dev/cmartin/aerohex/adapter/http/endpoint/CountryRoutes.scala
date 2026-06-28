package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.{CountryDto, CreateCountryRequest, UpdateCountryRequest}
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.port.in.*
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class CountryRoutes(
    findSvc: FindCountryUseCase,
    createSvc: CreateCountryUseCase,
    updateSvc: UpdateCountryUseCase,
    deleteSvc: DeleteCountryUseCase
):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    CountryEndpoints.findAll.zServerLogic { (page, pageSize) =>
      ZIO.logDebug(s"findAll - page: $page, pageSize: $pageSize") *>
        findSvc
          .findAll(Pagination(page, pageSize))
          .map(_.map(CountryDto.fromDomain))
          .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.searchByName.zServerLogic { q =>
      ZIO.logDebug(s"searchByName - q: $q") *>
        findSvc
          .searchByName(q)
          .map(_.map(CountryDto.fromDomain))
          .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.findByCode.zServerLogic { code =>
      ZIO.logDebug(s"findByCode - code: $code") *>
        findSvc
          .findByCode(code)
          .map(CountryDto.fromDomain)
          .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.create.zServerLogic { req =>
      ZIO.logDebug(s"create - request: $req") *>
        createSvc
          .create(CreateCountryRequest.toCommand(req))
          .map { country =>
            val dto = CountryDto.fromDomain(country)
            (dto, s"/api/v1/countries/${dto.code}")
          }
          .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.update.zServerLogic { (code, req) =>
      ZIO.logDebug(s"update - code: $code, request: $req") *>
        updateSvc
          .update(UpdateCountryRequest.toCommand(code, req))
          .map(CountryDto.fromDomain)
          .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.delete.zServerLogic { code =>
      ZIO.logDebug(s"delete - code: $code") *>
        deleteSvc
          .delete(code)
          .mapError(ErrorMapper.toHttpError)
    }
  )

object CountryRoutes:
  val layer: URLayer[
    FindCountryUseCase & CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase,
    CountryRoutes
  ] =
    ZLayer.fromFunction(new CountryRoutes(_, _, _, _))
