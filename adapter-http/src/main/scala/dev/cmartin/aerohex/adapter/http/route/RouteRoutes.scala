package dev.cmartin.aerohex.adapter.http.route

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.RouteWithAirportNames
import dev.cmartin.aerohex.domain.route.{AssociateAirlineUseCase, CreateRouteCommand, CreateRouteUseCase}
import dev.cmartin.aerohex.domain.route.{DisassociateAirlineUseCase, FindRouteUseCase, FindRoutesByAirlineUseCase}
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class RouteRoutes(
    createSvc: CreateRouteUseCase,
    associateSvc: AssociateAirlineUseCase,
    disassociateSvc: DisassociateAirlineUseCase,
    findByAirlineSvc: FindRoutesByAirlineUseCase,
    findRouteSvc: FindRouteUseCase,
    tokenService: TokenService
):
  private val secured = SecuredEndpoint.securityLogic(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    RouteEndpoints.create.zServerSecurityLogic(secured).serverLogic { _ => req =>
      createSvc
        .create(CreateRouteCommand(req.originIata, req.destinationIata, req.distanceKm))
        .map(RouteDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    RouteEndpoints.findAll.zServerSecurityLogic(secured).serverLogic { _ => (origin, destination, page, pageSize) =>
      val pagination                                           = Pagination(page, pageSize)
      val routes: IO[DomainError, List[RouteWithAirportNames]] = (origin, destination) match
        case (Some(o), Some(d)) =>
          findRouteSvc
            .findBySegment(IataCode.unsafeMake(o), IataCode.unsafeMake(d))
            .map(List(_))
            .catchSome { case DomainError.RouteNotFound(_, _) => ZIO.succeed(Nil) }
        case (Some(o), None)    =>
          findRouteSvc.findByOrigin(IataCode.unsafeMake(o), pagination)
        case (None, Some(d))    =>
          findRouteSvc.findByDestination(IataCode.unsafeMake(d), pagination)
        case (None, None)       =>
          findRouteSvc.findAll(pagination)
      routes.map(_.map(RouteDto.fromDomainWithNames)).mapError(ErrorMapper.toHttpError)
    },
    RouteEndpoints.associate.zServerSecurityLogic(secured).serverLogic { _ => (origin, destination, icao) =>
      associateSvc
        .associate(origin, destination, icao)
        .mapError(ErrorMapper.toHttpError)
    },
    RouteEndpoints.disassociate.zServerSecurityLogic(secured).serverLogic { _ => (origin, destination, icao) =>
      disassociateSvc
        .disassociate(origin, destination, icao)
        .mapError(ErrorMapper.toHttpError)
    },
    RouteEndpoints.findByAirline.zServerSecurityLogic(secured).serverLogic { _ => (icao, page, pageSize) =>
      findByAirlineSvc
        .findByAirline(icao, Pagination(page, pageSize))
        .map(_.map(RouteDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object RouteRoutes:
  val layer: URLayer[
    CreateRouteUseCase & AssociateAirlineUseCase & DisassociateAirlineUseCase & FindRoutesByAirlineUseCase &
      FindRouteUseCase & TokenService,
    RouteRoutes
  ] =
    ZLayer.fromFunction(new RouteRoutes(_, _, _, _, _, _))
