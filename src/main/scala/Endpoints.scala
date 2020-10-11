import java.time.LocalTime

import Validators._
import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import io.circe.generic.auto._
import models._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, ParseResult, QueryParamDecoder, Response}

class Endpoints(private val busInfoService: BusInfoService) {
  val busInfo = HttpRoutes
    .of[IO] {
      case GET -> Root / IntVar(busStopCode) :? BusNumber(bus) +& Hour(hour) =>
        hour match {
          case Some(Valid(h)) =>
            busInfoService
              .getNextBuses(BusRequest(busStopCode, bus, Some(h)))
              .flatMap(translateToHttpResponse)

          case Some(Invalid(e)) => BadRequest(e.map(_.sanitized))

          case None =>
            busInfoService
              .getNextBuses(BusRequest(busStopCode, bus))
              .flatMap(translateToHttpResponse)
        }

      case GET -> Root / IntVar(busStopCode) / "info" =>
        busInfoService
          .findBusStop(busStopCode)
          .foldF(NotFound(s"no bus stop with code $busStopCode"))(busStop => Ok(busStop))

      case GET -> Root / "" => BadRequest("missing busStop path")
      case GET -> Root / invalid => BadRequest(s"Invalid busStop: $invalid")
    }

  private def translateToHttpResponse(busResponse: BusInfoResponse): IO[Response[IO]] = {
    busResponse match {
      case r: NoBus => Ok(r)
      case r: BusNotHandled => BadRequest(r)
      case r: BusStopNotHandled => NotFound(r)
      case r: Failure => BadRequest(r)
      case r: Successful => Ok(r.buses)
    }
  }

}

object HealthRoutes {
  val liveness = HttpRoutes
    .of[IO] {
      case GET -> Root / "health" => Ok("Up and running")
    }
}

object Validators {

  object BusNumber extends OptionalQueryParamDecoderMatcher[String]("bus")

  object Hour extends OptionalValidatingQueryParamDecoderMatcher[LocalTime]("hour")

  private implicit val timeQueryDecoder: QueryParamDecoder[LocalTime] =
    QueryParamDecoder[String].emap(h =>
      ParseResult.fromTryCatchNonFatal(s"Invalid date format '$h'")(LocalTime.parse(h))
    )
}
