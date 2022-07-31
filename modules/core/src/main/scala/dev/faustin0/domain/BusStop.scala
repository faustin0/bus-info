package dev.faustin0.domain

import cats.implicits._

import scala.util.Try
import scala.xml.NodeSeq

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