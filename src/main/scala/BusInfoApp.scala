import cats.effect.{ ExitCode, IO, IOApp }
import cats.implicits._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import repositories.BusStopRepository

import scala.concurrent.ExecutionContext.global

object BusInfoApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val application = for {
      tperClient     <- HelloBusClient.make(global)
      busStopRepo    <- BusStopRepository.make
      busInfoService = BusInfoService(tperClient, busStopRepo)
      endpoints      = Endpoints(busInfoService)
    } yield Router(
      "/" -> (endpoints.nextBusRoutes <+> endpoints.busInfoRoutes <+> endpoints.swaggerRoutes),
      "/" -> endpoints.healthCheckRoutes
    ).orNotFound

    application.use { app =>
      val loggedApp = Logger.httpApp(logHeaders = false, logBody = true)(app)

      BlazeServerBuilder[IO](global)
        .bindHttp(80, "0.0.0.0")
        .withHttpApp(loggedApp)
        .serve
        .compile
        .drain
    }.as(ExitCode.Success)
  }
}
