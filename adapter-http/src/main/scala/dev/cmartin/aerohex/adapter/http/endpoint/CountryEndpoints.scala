package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.{CountryDto, CreateCountryRequest, UpdateCountryRequest}
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, ErrorMapper, HttpErrorResponse}
import dev.cmartin.aerohex.domain.port.in.*
import dev.cmartin.aerohex.shared.Pagination
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import io.circe.generic.auto.*
import zio.*

object CountryEndpoints {

  private val base = endpoint.in("api" / "v1" / "countries")

  // #6 reusable validated path param — auto-rejects non-alpha or wrong-length codes with 400
  private val codeParam =
    path[String]("code")
      .description("ISO 3166-1 alpha-2 country code (e.g. ES).")
      .validate(Validator.minLength(2))
      .validate(Validator.maxLength(2))
      .validate(Validator.pattern("[a-zA-Z]{2}"))

  private val notFoundErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Country not found."),
      EndpointErrors.unexpectedError
    )

  private val conflictErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.conflictVariant("Country already exists."),
      EndpointErrors.unexpectedError
    )

  // #5 oneOf with single default variant makes the error contract explicit in the OpenAPI spec
  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[CountryDto], Any] =
    base.get
      .summary("List countries")
      .description("Returns a paginated list of all countries.")
      .tag("Countries")
      .in(query[Int]("page").description("Page number (1-based).").default(1))
      .in(query[Int]("pageSize").description("Number of results per page.").default(20))
      .out(jsonBody[List[CountryDto]].description("List of countries."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  // #5 badRequestVariant declared because Tapir schema validation on `q` can emit 400
  val searchByName: PublicEndpoint[String, (StatusCode, HttpErrorResponse), List[CountryDto], Any] =
    base.get
      .summary("Search countries by name")
      .description(
        "Returns all countries whose name contains the given query string (case-insensitive). Query must be at least 3 characters."
      )
      .tag("Countries")
      .in("search")
      .in(
        query[String]("q")
          .description("Name fragment to search for (minimum 3 characters).")
          .validate(Validator.minLength(3))
          .example("rep")
      )
      .out(
        jsonBody[List[CountryDto]]
          .description("Matching countries.")
          .example(
            List(
              CountryDto("CZ", "Czech Republic"),
              CountryDto("DO", "Dominican Republic"),
              CountryDto("KR", "Republic of Korea")
            )
          )
      )
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.badRequestVariant("Invalid search query."),
          EndpointErrors.unexpectedError
        )
      )

  val findByCode: PublicEndpoint[String, (StatusCode, HttpErrorResponse), CountryDto, Any] =
    base.get
      .summary("Find country by code")
      .description("Returns a single country identified by its ISO 3166-1 alpha-2 code.")
      .tag("Countries")
      .in(codeParam)
      .out(jsonBody[CountryDto].description("The requested country."))
      .errorOut(notFoundErrorOut)

  // #4 Location header carries the canonical URL of the created resource (HTTP best practice)
  val create: PublicEndpoint[CreateCountryRequest, (StatusCode, HttpErrorResponse), (CountryDto, String), Any] =
    base.post
      .summary("Create country")
      .description("Creates a new country.")
      .tag("Countries")
      .in(jsonBody[CreateCountryRequest])
      .out(
        statusCode(StatusCode.Created)
          .and(jsonBody[CountryDto].description("The created country."))
          .and(header[String]("Location"))
      )
      .errorOut(conflictErrorOut)

  val update: PublicEndpoint[(String, UpdateCountryRequest), (StatusCode, HttpErrorResponse), CountryDto, Any] =
    base.put
      .summary("Update country")
      .description("Updates the name of an existing country.")
      .tag("Countries")
      .in(codeParam)
      .in(jsonBody[UpdateCountryRequest])
      .out(jsonBody[CountryDto].description("The updated country."))
      .errorOut(notFoundErrorOut)

  val delete: PublicEndpoint[String, (StatusCode, HttpErrorResponse), Unit, Any] =
    base.delete
      .summary("Delete country")
      .description("Deletes a country by its ISO 3166-1 alpha-2 code.")
      .tag("Countries")
      .in(codeParam)
      .out(statusCode(StatusCode.NoContent))
      .errorOut(notFoundErrorOut)

  // #1 Returns server endpoints so HttpServer can aggregate all resources before calling toHttp once
  def serverEndpoints(
      findSvc: FindCountryUseCase,
      createSvc: CreateCountryUseCase,
      updateSvc: UpdateCountryUseCase,
      deleteSvc: DeleteCountryUseCase
  ): List[ZServerEndpoint[Any, Any]] =
    List(
      findAll.zServerLogic { (page, pageSize) =>
        ZIO.logDebug(s"findAll - page: $page, pageSize: $pageSize") *>
          findSvc
            .findAll(Pagination(page, pageSize))
            .map(_.map(CountryDto.fromDomain))
            .mapError(ErrorMapper.toHttpError)
      },
      searchByName.zServerLogic { q =>
        ZIO.logDebug(s"searchByName - q: $q") *>
          findSvc
            .searchByName(q)
            .map(_.map(CountryDto.fromDomain))
            .mapError(ErrorMapper.toHttpError)
      },
      findByCode.zServerLogic { code =>
        ZIO.logDebug(s"findByCode - code: $code") *>
          findSvc
            .findByCode(code)
            .map(CountryDto.fromDomain)
            .mapError(ErrorMapper.toHttpError)
      },
      create.zServerLogic { req =>
        ZIO.logDebug(s"create - request: $req") *>
          createSvc
            .create(CreateCountryRequest.toCommand(req))
            .map { country =>
              val dto = CountryDto.fromDomain(country)
              (dto, s"/api/v1/countries/${dto.code}")
            }
            .mapError(ErrorMapper.toHttpError)
      },
      update.zServerLogic { (code, req) =>
        ZIO.logDebug(s"update - code: $code, request: $req") *>
          updateSvc
            .update(UpdateCountryRequest.toCommand(code, req))
            .map(CountryDto.fromDomain)
            .mapError(ErrorMapper.toHttpError)
      },
      delete.zServerLogic { code =>
        ZIO.logDebug(s"delete - code: $code") *>
          deleteSvc
            .delete(code)
            .mapError(ErrorMapper.toHttpError)
      }
    )
}
