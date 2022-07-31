package dev.faustin0.domain

case class BusStop(
  code: Int,
  name: String,
  location: String,
  comune: String,
  areaCode: Int,
  position: Position
)

case class Position(
  x: Long,
  y: Long,
  lat: Double,
  long: Double
)
