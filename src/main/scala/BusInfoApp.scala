import cats.effect.{ExitCode, IO, IOApp, Resource}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{Logger => ClientLogger}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import repositories.BusStopRepository

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object BusInfoApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val helloBusClientRes = BlazeClientBuilder[IO](global)
      .withConnectTimeout(5 seconds)
      .withRequestTimeout(7 seconds)
      .resource
      .map(client => ClientLogger(logHeaders = false, logBody = true)(client))
      .map(client => HelloBusClient(client))

    val application = for {
      tperClient <- helloBusClientRes
      busStopRepo <- Resource.liftF(
        IO.fromTry(
          BusStopRepository
            .makeFromAws()
            .orElse(BusStopRepository.makeFromEnv())
        )
      )
      helloBusRoutes = new Routes(tperClient)
      busInfoRoutes  = new InfoRoutes(busStopRepo)
    } yield Router(
      "/api" -> helloBusRoutes.helloBusService,
      "/api" -> busInfoRoutes.busInfoService
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
