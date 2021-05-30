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

object BusStop {

  def fromXml(xml: NodeSeq): Either[TransformError, BusStop] = {
    val busStopXml = xml \\ "Table"

    Try {
      BusStop(
        code = (busStopXml \ "codice").text.toInt,
        name = (busStopXml \ "denominazione").text.replace('`', ' ').trim,
        location = (busStopXml \ "ubicazione").text.replace('`', ' ').trim,
        comune = (busStopXml \ "comune").text.trim,
        areaCode = (busStopXml \ "codice_zona").text.toInt,
        position = Position(
          x = (busStopXml \ "coordinata_x").text.toLong,
          y = (busStopXml \ "coordinata_y").text.toLong,
          lat = (busStopXml \ "latitudine").text.toDouble,
          long = (busStopXml \ "longitudine").text.toDouble
        )
      )
    }.toEither
      .leftMap(t => TransformError(s"failed to parse xml: ${xml.text}", Some(t)))
  }
}
