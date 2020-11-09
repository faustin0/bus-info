import java.time.LocalTime

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits.catsSyntaxEitherId
import io.circe.generic.auto._
import models.BusInfoResponse.GenericDerivation._
import models._
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.RichHttp4sHttpEndpoint

class EndpointsTapir private (private val busInfoService: BusInfoDSL[IO])(implicit
  cs: ContextShift[IO],
  timer: Timer[IO]
) {

  val nextBus = endpoint.get
    .in(path[Int]("busStopCode"))
    .in(query[Option[String]]("bus"))
    .in(query[Option[LocalTime]]("hour"))
    .out(jsonBody[BusInfoResponse])
    .errorOut(
      oneOf[BusInfoResponse](
        statusMapping(StatusCode.BadRequest, jsonBody[BusNotHandled]),
        statusMapping(StatusCode.BadRequest, jsonBody[Failure]),
        statusMapping(StatusCode.NotFound, jsonBody[BusStopNotHandled])
      )
    )

  val busInfo = endpoint.get
    .in(path[Int]("busStopCode"))
    .in("info")
    .out(jsonBody[BusStop])
    .errorOut(oneOf[String](statusMapping(StatusCode.NotFound, jsonBody[String])))

  val healthcheck = endpoint.get
    .in("health")
    .out(jsonBody[String])

  val busInfoRoutes: HttpRoutes[IO] = busInfo.toRoutes { busStopCode =>
    busInfoService
      .findBusStop(busStopCode)
      .value
      .map(_.fold(s"no bus stop with code $busStopCode".asLeft[BusStop])(_.asRight[String]))
  }

  val nextBusRoutes: HttpRoutes[IO] = nextBus.toRoutes { input =>
    val (busStopCode, bus, hour) = input
    busInfoService.getNextBuses(BusRequest(busStopCode, bus, hour)).map {
      case x: NoBus             => x.asRight
      case x: Successful        => x.asRight
      case x: BusNotHandled     => x.asLeft
      case x: BusStopNotHandled => x.asLeft
      case x: Failure           => x.asLeft
    }
  }

  val healthcheckRoutes = healthcheck.toRoutes(_ => IO("Up and running".asRight[Unit]))
}

object EndpointsTapir {
  def apply(
    busInfoService: BusInfoDSL[IO]
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): EndpointsTapir =
    new EndpointsTapir(busInfoService)(cs, timer)
}
