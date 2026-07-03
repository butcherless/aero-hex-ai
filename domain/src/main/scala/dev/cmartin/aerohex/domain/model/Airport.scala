package dev.cmartin.aerohex.domain.model

opaque type IataCode = String

object IataCode {
  def apply(value: String): IataCode        = value
  extension (i: IataCode) def value: String = i
}

case class Airport(
    iataCode: IataCode,
    icaoCode: String,
    name: String,
    city: String,
    countryCode: CountryCode
)
