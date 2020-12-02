import java.time.LocalTime

import Endpoints.{ busInfo, healthcheck, nextBus }
import cats.effect.{ ContextShift, IO, Timer }
import cats.implicits.catsSyntaxEitherId
import io.circe.generic.auto._
import models.BusInfoResponse.GenericDerivation._
import models._
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.server.http4s.RichHttp4sHttpEndpoint
import sttp.tapir.swagger.http4s.SwaggerHttp4s

class Endpoints private (private val busInfoService: BusInfoDSL[IO])(implicit
  cs: ContextShift[IO],
  timer: Timer[IO]
) {

  val busInfoRoutes: HttpRoutes[IO] = busInfo.toRoutes { busStopCode =>
    busInfoService
      .findBusStop(busStopCode)
      .value
      .map(_.fold(s"no bus stop with code $busStopCode".asLeft[BusStop])(_.asRight[String]))
  }

  val nextBusRoutes: HttpRoutes[IO] = nextBus.toRoutes { input =>
    val (busStopCode, bus, hour) = input
    busInfoService.getNextBuses(BusRequest(busStopCode, bus, hour)).map {
      case x: NoBus      => x.asRight
      case x: Successful => x.asRight
      case x             => x.asLeft
    }
  }

  val healthCheckRoutes = healthcheck.toRoutes(_ => IO("Up and running".asRight[Unit]))

  val swaggerRoutes = new SwaggerHttp4s(
    List(Endpoints.busInfo, Endpoints.nextBus)
      .toOpenAPI("The bus-info API", "0.0.1")
      .toYaml
  ).routes[IO]
}

object Endpoints {

  def apply(
    busInfoService: BusInfoDSL[IO]
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): Endpoints =
    new Endpoints(busInfoService)(cs, timer)

  private val baseEndpoint = endpoint.in("bus-stops")

  val nextBus = baseEndpoint.get
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

  val busInfo = baseEndpoint.get
    .in(path[Int]("busStopCode"))
    .in("info")
    .out(jsonBody[BusStop])
    .errorOut(oneOf[String](statusMapping(StatusCode.NotFound, jsonBody[String])))

  val healthcheck = endpoint.get
    .in("health")
    .out(jsonBody[String])
}
