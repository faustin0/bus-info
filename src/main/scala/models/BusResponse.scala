package models

import cats.implicits.toFunctorOps
import io.circe.{ Decoder, Encoder }
import io.circe.syntax.EncoderOps
import io.circe.generic.auto._

import scala.xml.Elem

sealed trait BusInfoResponse extends Product with Serializable

final case class NoBus(message: String) extends BusInfoResponse

final case class BusNotHandled(msg: String) extends BusInfoResponse

final case class BusStopNotHandled(msg: String) extends BusInfoResponse

final case class Successful(buses: List[BusResponse]) extends BusInfoResponse

final case class Failure(error: String) extends BusInfoResponse

case class BusResponse(
  busStopCode: Int,
  bus: String,
  satellite: Boolean,
  hour: String,
  busInfo: String = ""
)

object BusInfoResponse {

  private val responseRegex       = """^(\S+) (\w+) (\d+:\d+)( .*)*$""".r
  private val previsionRegex      = """\(x\d+:\d+\)""".r
  private val notHandledBusRegex  = """.*LINEA.*NON GESTITA.*""".r
  private val notHandledStopRegex = """.*FERMATA.*NON GESTITA.*""".r

  def fromXml(xml: Elem, busStopCode: Int): Either[TransformError, BusInfoResponse] = {
    val textResponse = xml \\ "string"

    val content = textResponse
      .map(_.text)
      .map(_.trim)
      .head

    content match {
      case notHandledBusRegex(_*)              => Right(BusNotHandled("bus not handled"))
      case notHandledStopRegex(_*)             => Right(BusStopNotHandled("bus-stop not handled"))
      case msg if msg.contains("NESSUNA")      => Right(NoBus(msg))
      case msg if msg.contains("TperHellobus") => extractBusResponse(msg, busStopCode).map(Successful)
      case _                                   => Right(Failure("Unsupported response"))
    }
  }

  private def extractBusResponse(response: String, busStopCode: Int): Either[TransformError, List[BusResponse]] = {

    val splitResponse = response
      .replace("TperHellobus:", "")
      .replaceFirst(previsionRegex.regex, "")
      .split(",")
      .toList
      .map(_.trim)

    splitResponse.partitionMap {
      case responseRegex(bus, satellite, hour, null) => //info may be omitted
        Right(BusResponse(busStopCode, bus, satellite.contains("Satellite"), hour))
      case responseRegex(bus, satellite, hour, info) =>
        Right(BusResponse(busStopCode, bus, satellite.contains("Satellite"), hour, info.trim))
      case failedToMatch                             =>
        Left(TransformError(s"Failed to match '$failedToMatch'"))
    } match {
      case (Nil, matchedResp) => Right(matchedResp)
      case (errors, _)        => Left(errors.head)
    }
  }

  object GenericDerivation {

    implicit val encodeBusInfoResponse: Encoder[BusInfoResponse] = Encoder.instance {
      case noBus @ NoBus(_)                     => noBus.asJson
      case busNotHandled @ BusNotHandled(_)     => busNotHandled.asJson
      case busStopNotHandled: BusStopNotHandled => busStopNotHandled.asJson
      case success: Successful                  => success.buses.asJson
      case failure @ Failure(_)                 => failure.asJson
    }

    implicit val decodeBusInfoResponse: Decoder[BusInfoResponse] =
      List[Decoder[BusInfoResponse]](
        Decoder[NoBus].widen,
        Decoder[BusNotHandled].widen,
        Decoder[BusStopNotHandled].widen,
        Decoder[Successful].widen,
        Decoder[Failure].widen
      ).reduceLeft(_ or _)
  }
}
