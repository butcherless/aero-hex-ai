package dev.cmartin.aerohex.domain.model

opaque type CountryCode = String

object CountryCode {
  def apply(value: String): CountryCode        = value
  def from(value: String): Option[CountryCode] =
    Option.when(value.length == 2 && value.forall(_.isLetter))(value)
  extension (c: CountryCode) def value: String = c
}

case class Country(code: CountryCode, name: String)
