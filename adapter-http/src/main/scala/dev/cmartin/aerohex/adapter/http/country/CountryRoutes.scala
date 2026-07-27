package dev.cmartin.aerohex.adapter.http.country

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.country.*
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.{URLayer, ZLayer}

class CountryRoutes(
    findSvc: FindCountryUseCase,
    createSvc: CreateCountryUseCase,
    updateSvc: UpdateCountryUseCase,
    deleteSvc: DeleteCountryUseCase,
    tokenService: TokenService
):
  private val secured = SecuredEndpoint.securityLogic(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    CountryEndpoints.findAll.zServerSecurityLogic(secured).serverLogic { _ => (name, page, pageSize) =>
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
    CountryEndpoints.findByCode.zServerSecurityLogic(secured).serverLogic { _ => code =>
      findSvc
        .findByCode(CountryCode.unsafeMake(code))
        .map(CountryDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.create.zServerSecurityLogic(secured).serverLogic { _ => req =>
      CreateCountryRequest
        .toCommand(req)
        .flatMap(createSvc.create)
        .map { country =>
          val dto = CountryDto.fromDomain(country)
          (dto, s"/api/v1/countries/${dto.code}")
        }
        .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.update.zServerSecurityLogic(secured).serverLogic { _ => (code, req) =>
      updateSvc
        .update(UpdateCountryRequest.toCommand(code, req))
        .map(CountryDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    CountryEndpoints.delete.zServerSecurityLogic(secured).serverLogic { _ => code =>
      deleteSvc
        .delete(CountryCode.unsafeMake(code))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object CountryRoutes:
  val layer: URLayer[
    FindCountryUseCase & CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase & TokenService,
    CountryRoutes
  ] =
    ZLayer.fromFunction(new CountryRoutes(_, _, _, _, _))
