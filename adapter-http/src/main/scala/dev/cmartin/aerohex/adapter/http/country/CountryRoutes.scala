package dev.cmartin.aerohex.adapter.http.country

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.domain.country.*
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.ZServerEndpoint
import zio.{URLayer, ZLayer}

class CountryRoutes(
    findSvc: FindCountryUseCase,
    createSvc: CreateCountryUseCase,
    updateSvc: UpdateCountryUseCase,
    deleteSvc: DeleteCountryUseCase,
    tokenService: TokenService
):
  private val secured = SecuredEndpoint(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    secured(CountryEndpoints.findAll) { (name, page, pageSize) =>
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
    secured(CountryEndpoints.findByCode) { code =>
      findSvc
        .findByCode(CountryCode.unsafeMake(code))
        .map(CountryDto.fromDomain)
    },
    secured(CountryEndpoints.create) { req =>
      CreateCountryRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { country =>
          val dto = CountryDto.fromDomain(country)
          (dto, s"/api/v1/countries/${dto.code}")
        }
    },
    secured(CountryEndpoints.update) { (code, req) =>
      updateSvc
        .update(UpdateCountryRequest.toCommand(code, req))
        .map(CountryDto.fromDomain)
    },
    secured(CountryEndpoints.delete) { code =>
      deleteSvc
        .delete(CountryCode.unsafeMake(code))
    }
  )

object CountryRoutes:
  val layer: URLayer[
    FindCountryUseCase & CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase & TokenService,
    CountryRoutes
  ] =
    ZLayer.fromFunction(new CountryRoutes(_, _, _, _, _))
