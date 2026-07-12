package dev.cmartin.aerohex.adapter.http.country

import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.country.*
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
    CountryEndpoints.findAll.zServerLogic { (name, page, pageSize) =>
      val pagination = Pagination(page, pageSize)
      name match
        case Some(query) =>
          findSvc
            .searchByName(query)
            .map(_.slice(pagination.offset, pagination.offset + pagination.pageSize))
            .map(_.map(CountryDto.fromDomain))
        case None        =>
          findSvc
            .findAll(pagination)
            .map(_.map(CountryDto.fromDomain))
    },
    CountryEndpoints.findByCode.zServerLogic { code =>
      findSvc
        .findByCode(CountryCode.unsafeMake(code))
        .map(CountryDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.create.zServerLogic { req =>
      CreateCountryRequest
        .toCommand(req)
        .flatMap(createSvc.create)
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
        .delete(CountryCode.unsafeMake(code))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object CountryRoutes:
  val layer: URLayer[
    FindCountryUseCase & CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase,
    CountryRoutes
  ] =
    ZLayer.fromFunction(new CountryRoutes(_, _, _, _))
