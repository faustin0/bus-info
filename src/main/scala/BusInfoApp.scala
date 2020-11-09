import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import repositories.BusStopRepository
import sttp.tapir.docs.openapi._
import sttp.tapir.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.swagger.http4s.SwaggerHttp4s

import scala.concurrent.ExecutionContext.global

object BusInfoApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val application = for {
      tperClient <- HelloBusClient.make(global)
      busStopRepo <- BusStopRepository.make
      busInfoService = BusInfoService(tperClient, busStopRepo)
      endpoints = EndpointsTapir(busInfoService)
    } yield Router(
      "/api/bus-stops/" -> (endpoints.nextBusRoutes <+> endpoints.busInfoRoutes),
      "/api/" -> (endpoints.healthcheckRoutes <+> new SwaggerHttp4s(
        List(endpoints.busInfo, endpoints.nextBus, endpoints.healthcheck)
          .toOpenAPI("The bus-info API", "0.0.1")
          .toYaml
      ).routes[IO])
    ).orNotFound

    application
      .use(app => {
        val loggedApp = Logger.httpApp(logHeaders = false, logBody = true)(app)

        BlazeServerBuilder[IO](global)
          .bindHttp(80, "0.0.0.0")
          .withHttpApp(loggedApp)
          .serve
          .compile
          .drain
      })
      .as(ExitCode.Success)
  }
}
