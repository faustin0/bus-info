import java.time.LocalTime

import cats.data.Validated
import cats.effect.IO
import io.circe.generic.auto._
import models.{BusRequest, Invalid, NoBus, Successful}
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpRoutes, ParseResult, QueryParamDecoder}

class Routes(helloBusClient: HelloBusClient) {
  val helloBusService = HttpRoutes
    .of[IO] {
      case GET -> Root / "hello" / IntVar(busStop) :? BusNumber(bus) +& Hour(hour) =>
        hour match {
          case Some(Validated.Valid(h))   => execRequest(BusRequest(busStop, bus, Some(h)))
          case Some(Validated.Invalid(e)) => BadRequest(e.map(_.sanitized))
          case None                       => execRequest(BusRequest(busStop, bus))
        }

      case GET -> Root / "hello" / "" =>
        BadRequest(Invalid("missing busStop path"))

      case GET -> Root / "hello" / invalid =>
        BadRequest(Invalid(s"Invalid busStop: $invalid"))
    }
    .orNotFound

  private val execRequest = (busRequest: BusRequest) => {
    for {
      resp <- helloBusClient.hello(busRequest)
      restResp <- resp match {
        case r: NoBus      => NotFound(r)
        case r: Invalid    => BadRequest(r)
        case r: Successful => Ok(r.buses)
      }
    } yield restResp
  }

  object BusNumber extends OptionalQueryParamDecoderMatcher[String]("bus")
  object Hour      extends OptionalValidatingQueryParamDecoderMatcher[LocalTime]("hour")

  implicit val timeQueryDecoder: QueryParamDecoder[LocalTime] =
    QueryParamDecoder[String].emap(h =>
      ParseResult.fromTryCatchNonFatal(s"Invalid date format '$h'")(LocalTime.parse(h))
    )
}
