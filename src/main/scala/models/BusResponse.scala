package models

import cats.data.EitherT
import cats.effect.IO
import org.http4s.{DecodeFailure, EntityDecoder, MediaType, Message}

import scala.xml.Elem



sealed trait HelloBusResponse

case class NoBus(message: String) extends HelloBusResponse

case class Invalid(message: String) extends HelloBusResponse

case class Successful(buses: List[BusResponse]) extends HelloBusResponse

case class TransformError(error: String) extends IllegalStateException


case class BusResponse(
                        bus: String,
                        satellite: Boolean,
                        hour: String,
                        busInfo: String
                      )


object HelloBusResponse {

  //  private val responseRegex = """(\(x[\d\w:]+\) )*([\d\w]+) (\w+) (\d+:\d+)( .*)*""".r
  private val responseRegex = """([\d\w]+) (\w+) (\d+:\d+)( .*)*""".r
  private val punctuationCharacter = "\\p{P}".r

  def fromXml(xml: Elem): Either[TransformError, HelloBusResponse] = {
    val textResponse = xml \\ "string"

    val content = textResponse
      .map(_.text)
      .map(_.trim)
      .head

    content match {
      case msg if msg.contains("HellobusHelp") => Right(Invalid(msg))
      case msg if msg.contains("NESSUNA") => Right(NoBus(msg))
      case msg if msg.contains("TperHellobus") => extractBusResponse(msg).map(Successful)
      case _ => Right(Invalid("Unsupported response"))
    }
  }

  private def extractBusResponse(response: String): Either[TransformError, List[BusResponse]] = {

    val splitResponse = response
      .replace("TperHellobus:", "")
      .split(",")
      .toList
      .map(_.trim)

    splitResponse
      .partitionMap {
        case responseRegex(bus, satellite, hour, info) => Right(BusResponse(bus, satellite.contains("Satellite"), hour, info.trim))
        case failedToMatch => Left(TransformError(s"Failed to match '$failedToMatch'"))
      } match {
      case (Nil, matchedResp) => Right(matchedResp)
      case (errors, _) => Left(errors.head)
    }
  }

}