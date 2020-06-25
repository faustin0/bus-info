import cats.effect.{ExitCode, IO, IOApp, Resource}
import models.BusRequest
import org.http4s.HttpRoutes
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._


class Routes(helloBusClient: HelloBusClient) {
  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" => {
      val resp = helloBusClient.hello(BusRequest("27", 303))
      Ok(resp.toString()) //fixme decoder implicit
      //      Ok(resp)
    }

    case GET -> Root / "test" => Ok("test ok")
  }.orNotFound
}


object TperBoBotApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val routes: Resource[IO, Routes] = BlazeClientBuilder[IO](global)
      .withConnectTimeout(5 seconds)
      .withRequestTimeout(5 seconds)
      .resource
      .map(client => HelloBusClient(client))
      .map(tperClient => new Routes(tperClient))

    routes.use(routes => {
      val app = routes.helloWorldService
      val loggedApp = Logger.httpApp(logHeaders = false, logBody = true)(app)

      BlazeServerBuilder[IO](global)
        .bindHttp(8080, "localhost")
        .withHttpApp(loggedApp)
        .serve
        .compile
        .drain
    }).as(ExitCode.Success)
  }
}
