package dev.cmartin.aerohex.domain.model

opaque type Registration = String

object Registration {
  def apply(value: String): Registration        = value
  extension (r: Registration) def value: String = r
}

case class Aircraft(
    registration: Registration,
    typeCode: String,
    airlineIcao: IcaoCode
)
