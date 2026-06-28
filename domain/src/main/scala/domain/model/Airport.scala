package domain.model

opaque type IataCode = String

object IataCode {
  def apply(value: String): IataCode        = value
  extension (i: IataCode) def value: String = i
}

case class Airport(
    iata: IataCode,
    icaoCode: String,
    name: String,
    city: String,
    countryCode: CountryCode
)
