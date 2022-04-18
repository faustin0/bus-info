package dev.faustin0.api

import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import dev.faustin0.api.Endpoints.{ busStopByCode, busStopSearch, healthcheck, nextBus }
import dev.faustin0.domain.BusInfoResponse.GenericDerivation._
import dev.faustin0.domain._
import dev.faustin0.info.BuildInfo
import io.circe.generic.auto._
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.SwaggerUI

import java.time.LocalTime

class Endpoints private (private val busInfoService: BusInfoDSL[IO]) {

  private val http4sInterpreter = Http4sServerInterpreter[IO]()

  val busStopInfoRoutes: HttpRoutes[IO] = http4sInterpreter.toRoutes(busStopByCode.serverLogic[IO] { busStopCode =>
    busInfoService
      .getBusStop(busStopCode)
      .toRight(left = s"no bus stop with code $busStopCode")
      .value
  })

  val busStopSearchRoutes: HttpRoutes[IO] = http4sInterpreter.toRoutes(busStopSearch.serverLogic[IO] { busStopName =>
    busInfoService
      .searchBusStop(busStopName)
      .map(_.asRight[Unit])
  })

  val nextBusRoutes: HttpRoutes[IO] = http4sInterpreter.toRoutes(nextBus.serverLogic[IO] { input =>
    val (busStopCode, bus, hour) = input
    busInfoService.getNextBuses(BusRequest(busStopCode, bus, hour)).map {
      case x: Successful => x.asRight
      case x             => x.asLeft
    }
  })

  val healthCheckRoutes: HttpRoutes[IO] =
    http4sInterpreter.toRoutes(healthcheck.serverLogic[IO] { _ =>
      IO("Up and running".asRight[Unit])
    })

  val swaggerUIRoutes: HttpRoutes[IO] = {

    val openApiDocs: OpenAPI = OpenAPIDocsInterpreter()
      .toOpenAPI(
        List(Endpoints.busStopByCode, Endpoints.nextBus, Endpoints.busStopSearch),
        title = "The bus-info API",
        version = BuildInfo.version
      )

    val swaggerRoutes = SwaggerUI[IO](openApiDocs.toYaml)
    http4sInterpreter.toRoutes(swaggerRoutes)
  }

}

object Endpoints {

  def apply(
    busInfoService: BusInfoDSL[IO]
  ): Endpoints =
    new Endpoints(busInfoService)

  private val baseEndpoint = endpoint.in("bus-stops")

  val nextBus = baseEndpoint.get
    .in(path[Int]("busStopCode"))
    .in(query[Option[String]]("bus"))
    .in(query[Option[LocalTime]]("hour"))
    .out(jsonBody[BusInfoResponse])
    .errorOut(
      oneOf[BusInfoResponse](
        oneOfVariant(StatusCode.BadRequest, jsonBody[BusNotHandled]),
        oneOfVariant(StatusCode.BadRequest, jsonBody[Failure]),
        oneOfVariant(StatusCode.NotFound, jsonBody[BusStopNotHandled]),
        oneOfVariant(StatusCode.ServiceUnavailable, jsonBody[Suspended])
      )
    )

  val busStopByCode = baseEndpoint.get
    .in(path[Int]("busStopCode"))
    .in("info")
    .out(jsonBody[BusStop])
    .errorOut(oneOf[String](oneOfVariant(StatusCode.NotFound, jsonBody[String])))

  val busStopSearch = baseEndpoint.get
    .in(query[String]("name"))
    .out(jsonBody[List[BusStop]])

  val healthcheck = endpoint.get
    .in("health")
    .out(jsonBody[String])

}
