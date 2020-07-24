package models

import scala.xml.Elem

sealed trait HelloBusResponse

final case class NoBus(message: String) extends HelloBusResponse

final case class Invalid(error: String) extends HelloBusResponse

final case class Successful(buses: List[BusResponse]) extends HelloBusResponse



case class BusResponse(
  bus: String,
  satellite: Boolean,
  hour: String,
  busInfo: String = ""
)

object HelloBusResponse {

  private val responseRegex = """^(\S+) (\w+) (\d+:\d+)( .*)*$""".r
  private val previsionRegex = """\(x\d+:\d+\)""".r

  def fromXml(xml: Elem): Either[TransformError, HelloBusResponse] = {
    val textResponse = xml \\ "string"

    val content = textResponse
      .map(_.text)
      .map(_.trim)
      .head

    content match {
      case msg if msg.contains("HellobusHelp") => Right(Invalid(msg))
      case msg if msg.contains("NESSUNA")      => Right(NoBus(msg))
      case msg if msg.contains("TperHellobus") => extractBusResponse(msg).map(Successful)
      case _                                   => Right(Invalid("Unsupported response"))
    }
  }

  private def extractBusResponse(
    response: String
  ): Either[TransformError, List[BusResponse]] = {

    val splitResponse = response
      .replace("TperHellobus:", "")
      .replaceFirst(previsionRegex.regex, "")
      .split(",")
      .toList
      .map(_.trim)

    splitResponse
      .partitionMap {
        case responseRegex(bus, satellite, hour, null) => //info is omitted on estimated responses
          Right(BusResponse(bus, satellite.contains("Satellite"), hour))
        case responseRegex(bus, satellite, hour, info) =>
          Right(BusResponse(bus, satellite.contains("Satellite"), hour, info.trim))
        case failedToMatch =>
          Left(TransformError(s"Failed to match '$failedToMatch'"))
      } match {
      case (Nil, matchedResp) => Right(matchedResp)
      case (errors, _)        => Left(errors.head)
    }
  }

}
