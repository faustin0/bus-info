import java.time.LocalTime

import cats.data.Validated
import cats.effect.IO
import io.circe.generic.auto._
import models.{BusRequest, Invalid, NoBus, Successful}
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, ParseResult, QueryParamDecoder}
import repositories.BusStopRepository

class Routes(private val helloBusClient: HelloBusClient) {
  val helloBusService = HttpRoutes
    .of[IO] {
      case GET -> Root / IntVar(busStop) :? BusNumber(bus) +& Hour(hour) =>
        hour match {
          case Some(Validated.Valid(h))   => execRequest(BusRequest(busStop, bus, Some(h)))
          case Some(Validated.Invalid(e)) => BadRequest(e.map(_.sanitized))
          case None                       => execRequest(BusRequest(busStop, bus))
        }

      case GET -> Root / "" =>
        BadRequest(Invalid("missing busStop path"))

      case GET -> Root / invalid =>
        BadRequest(Invalid(s"Invalid busStop: $invalid"))
    }

  private def execRequest(busRequest: BusRequest) = {
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

  private implicit val timeQueryDecoder: QueryParamDecoder[LocalTime] =
    QueryParamDecoder[String].emap(h =>
      ParseResult.fromTryCatchNonFatal(s"Invalid date format '$h'")(LocalTime.parse(h))
    )
}

class InfoRoutes(private val busStopRepository: BusStopRepository) {
  val busInfoService = HttpRoutes
    .of[IO] {
      case GET -> Root / IntVar(busStopCode) / "info" =>
        busStopRepository
          .findBusStopByCode(busStopCode.toLong)
          .value
          .flatMap {
            case Some(busStop) => Ok(busStop)
            case None          => NotFound(s"no bus stop with code $busStopCode")
          }
    }
}

object HealthRoutes {
  val liveness = HttpRoutes
    .of[IO] {
      case GET -> Root / "health" => Ok("Up and running")
    }
}
