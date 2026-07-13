package dev.cmartin.aerohex.adapter.http.route

import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.route.{AssociateAirlineUseCase, CreateRouteCommand, CreateRouteUseCase}
import dev.cmartin.aerohex.domain.route.{DisassociateAirlineUseCase, FindRoutesByAirlineUseCase}
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class RouteRoutes(
    createSvc: CreateRouteUseCase,
    associateSvc: AssociateAirlineUseCase,
    disassociateSvc: DisassociateAirlineUseCase,
    findByAirlineSvc: FindRoutesByAirlineUseCase
):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    RouteEndpoints.create.zServerLogic { req =>
      createSvc
        .create(CreateRouteCommand(req.originIata, req.destinationIata, req.distanceKm))
        .map(RouteDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    },
    RouteEndpoints.associate.zServerLogic { (origin, destination, icao) =>
      associateSvc
        .associate(origin, destination, icao)
        .mapError(ErrorMapper.toHttpError)
    },
    RouteEndpoints.disassociate.zServerLogic { (origin, destination, icao) =>
      disassociateSvc
        .disassociate(origin, destination, icao)
        .mapError(ErrorMapper.toHttpError)
    },
    RouteEndpoints.findByAirline.zServerLogic { (icao, page, pageSize) =>
      findByAirlineSvc
        .findByAirline(icao, Pagination(page, pageSize))
        .map(_.map(RouteDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object RouteRoutes:
  val layer: URLayer[
    CreateRouteUseCase & AssociateAirlineUseCase & DisassociateAirlineUseCase & FindRoutesByAirlineUseCase,
    RouteRoutes
  ] =
    ZLayer.fromFunction(new RouteRoutes(_, _, _, _))
