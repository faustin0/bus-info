import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{Logger => ClientLogger}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object BusInfoApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val routes = BlazeClientBuilder[IO](global)
      .withConnectTimeout(5 seconds)
      .withRequestTimeout(7 seconds)
      .resource
      .map(client => ClientLogger(logHeaders = false, logBody = true)(client))
      .map(client => HelloBusClient(client))
      .map(tperClient => new Routes(tperClient))

    routes
      .use(routes => {
        val app       = routes.helloBusService
        val loggedApp = Logger.httpApp(logHeaders = false, logBody = true)(app)

        BlazeServerBuilder[IO](global)
          .bindHttp(8080, "localhost")
          .withHttpApp(loggedApp)
          .serve
          .compile
          .drain
      })
      .as(ExitCode.Success)
  }
}
