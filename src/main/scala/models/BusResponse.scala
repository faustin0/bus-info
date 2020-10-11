package models

import scala.xml.Elem

sealed trait BusInfoResponse extends Product with Serializable

final case class NoBus(message: String) extends BusInfoResponse

final case class BusNotHandled(msg: String) extends BusInfoResponse

final case class BusStopNotHandled(msg: String) extends BusInfoResponse

final case class Successful(buses: List[BusResponse]) extends BusInfoResponse

final case class Failure(error: String) extends BusInfoResponse

case class BusResponse(
                        bus: String,
                        satellite: Boolean,
                        hour: String,
                        busInfo: String = ""
                      )

object BusInfoResponse {

  private val responseRegex = """^(\S+) (\w+) (\d+:\d+)( .*)*$""".r
  private val previsionRegex = """\(x\d+:\d+\)""".r
  private val notHandledBusRegex = """.*LINEA.*NON GESTITA.*""".r
  private val notHandledStopRegex = """.*FERMATA.*NON GESTITA.*""".r

  def fromXml(xml: Elem): Either[TransformError, BusInfoResponse] = {
    val textResponse = xml \\ "string"

    val content = textResponse
      .map(_.text)
      .map(_.trim)
      .head

    content match {
      case notHandledBusRegex(_*) => Right(BusNotHandled("bus not handled"))
      case notHandledStopRegex(_*) => Right(BusStopNotHandled("bus-stop not handled"))
      case msg if msg.contains("NESSUNA") => Right(NoBus(msg))
      case msg if msg.contains("TperHellobus") => extractBusResponse(msg).map(Successful)
      case _ => Right(Failure("Unsupported response"))
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
        case responseRegex(bus, satellite, hour, null) => //info may be omitted
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
