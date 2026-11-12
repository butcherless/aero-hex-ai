package dev.cmartin.aerohex.infrastructure.persistence.quill.common

// Shared by every repository's searchByName (Country, Airport, Airline).
private[quill] object QuillSearch:

  // Upper bound on rows a name search returns. It matches the largest pageSize the paginated
  // endpoints accept, since search has no pagination of its own.
  val MaxResults: Int = 100

  // Builds an ILIKE "contains" pattern from user input. The input is escaped, so `%` and `_` in the
  // query match those literal characters instead of acting as wildcards. Backslash is PostgreSQL's
  // default LIKE escape character, so it is escaped first.
  def containsPattern(query: String): String =
    val escaped = query.flatMap {
      case c @ ('\\' | '%' | '_') => s"\\$c"
      case c                      => c.toString
    }
    s"%$escaped%"
