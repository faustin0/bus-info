package dev.faustin0.importer.domain

import cats.syntax.all._
import dev.faustin0.domain.{ BusStop, Position, TransformError }

import scala.util.Try
import scala.xml.NodeSeq

object BusStopDecoder extends XmlDecoder[BusStop] {

  override def decode(xml: NodeSeq): Either[TransformError, BusStop] = {
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
