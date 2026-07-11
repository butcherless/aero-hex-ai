package dev.cmartin.aerohex.adapter.http.endpoint

import sttp.tapir.*

// Shared page/pageSize query params for every paginated findAll-style endpoint — same
// validated shape everywhere (BR-12: page >= 1, pageSize between 1 and 100).
object PaginationParams:
  val page: EndpointInput[Int] =
    query[Int]("page").description("Page number (1-based).").default(1).validate(Validator.min(1))

  val pageSize: EndpointInput[Int] =
    query[Int]("pageSize")
      .description("Number of results per page (1–100).")
      .default(20)
      .validate(Validator.min(1))
      .validate(Validator.max(100))
