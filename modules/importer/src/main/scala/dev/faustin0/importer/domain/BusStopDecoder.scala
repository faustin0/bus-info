package dev.faustin0.importer.domain

import cats.syntax.all._
import dev.faustin0.domain.{ BusStop, Position, TransformError }

import java.text.Normalizer
import java.text.Normalizer.Form
import scala.util.Try
import scala.xml.NodeSeq

object BusStopDecoder extends XmlDecoder[BusStop] {

  override def decode(xml: NodeSeq): Either[TransformError, BusStop] = {
    val busStopXml = xml \\ "Table"

    Try {
      BusStop(
        code = (busStopXml \ "codice").text.normalized.toInt,
        name = (busStopXml \ "denominazione").text.fullNormalized,
        location = (busStopXml \ "ubicazione").text.normalized,
        comune = (busStopXml \ "comune").text.fullNormalized,
        areaCode = (busStopXml \ "codice_zona").text.normalized.toInt,
        position = Position(
          x = (busStopXml \ "coordinata_x").text.normalized.toLong,
          y = (busStopXml \ "coordinata_y").text.normalized.toLong,
          lat = (busStopXml \ "latitudine").text.normalized.toDouble,
          long = (busStopXml \ "longitudine").text.normalized.toDouble
        )
      )
    }.toEither
      .leftMap(t => TransformError(s"failed to parse xml: ${xml.text}", Some(t)))
  }

  private val squareBracketRegex = """\[.*?]""".r   // Match [xxx]
  private val allNonWord         = """[^\w\s-]""".r // Match all non-word, non-space or non-dash characters
  private val whiteSpaces        = """\s+""".r      // Match whitespace (including newlines and repetitions)

  implicit final class NormalizeOps(val s: String) extends AnyVal {

    def fullNormalized: String =
      Normalizer
        .normalize(s, Form.NFD)
        .replace('`', ' ')
        .replaceAll(squareBracketRegex.regex, "")
        .replaceAll(allNonWord.regex, " ")
        .replace('-', ' ') // Replace dashes with spaces
        .trim
        .replaceAll(whiteSpaces.regex, " ")

    def normalized: String = Normalizer.normalize(s, Form.NFD)

  }

}
