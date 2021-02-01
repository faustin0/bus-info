package dev.faustin0.api

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits.catsSyntaxEitherId
import dev.faustin0.api.Endpoints.{busStopByCode, busStopSearch, healthcheck, nextBus}
import dev.faustin0.domain.BusInfoResponse.GenericDerivation._
import dev.faustin0.domain._
import io.circe.generic.auto._
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.http4s.SwaggerHttp4s

import java.time.LocalTime

class Endpoints private (private val busInfoService: BusInfoDSL[IO])(implicit
  cs: ContextShift[IO],
  timer: Timer[IO]
) {

  val busStopInfoRoutes: HttpRoutes[IO] = Http4sServerInterpreter.toRoutes(busStopByCode) { busStopCode =>
    busInfoService
      .getBusStop(busStopCode)
      .value
      .map(_.fold(s"no bus stop with code $busStopCode".asLeft[BusStop])(_.asRight[String]))
  }

  val busStopSearchRoutes: HttpRoutes[IO] = Http4sServerInterpreter.toRoutes(busStopSearch) { busStopName =>
    busInfoService
      .searchBusStop(busStopName)
      .map(_.asRight[Unit])
  }

  val nextBusRoutes: HttpRoutes[IO] = Http4sServerInterpreter.toRoutes(nextBus) { input =>
    val (busStopCode, bus, hour) = input
    busInfoService.getNextBuses(BusRequest(busStopCode, bus, hour)).map {
      case x: Successful => x.asRight
      case x             => x.asLeft
    }
  }

  val healthCheckRoutes = Http4sServerInterpreter.toRoutes(healthcheck)(_ => IO("Up and running".asRight[Unit]))

  val swaggerRoutes = new SwaggerHttp4s(
    OpenAPIDocsInterpreter
      .toOpenAPI(
        List(Endpoints.busStopByCode, Endpoints.nextBus, Endpoints.busStopSearch),
        title = "The bus-info API",
        version = "0.0.1"
      )
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

  val busStopByCode = baseEndpoint.get
    .in(path[Int]("busStopCode"))
    .in("info")
    .out(jsonBody[BusStop])
    .errorOut(oneOf[String](statusMapping(StatusCode.NotFound, jsonBody[String])))

  val busStopSearch = baseEndpoint.get
    .in(query[String]("name"))
    .out(jsonBody[List[BusStop]])

  val healthcheck = endpoint.get
    .in("health")
    .out(jsonBody[String])
}
