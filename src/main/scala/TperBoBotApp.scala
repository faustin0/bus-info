import cats.effect.{ExitCode, IO, IOApp, Resource}
import models.{BusRequest, Invalid, NoBus, Successful}
import org.http4s.HttpRoutes
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.circe.CirceEntityEncoder._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class Routes(helloBusClient: HelloBusClient) {
  val helloBusService = HttpRoutes
    .of[IO] {
      case GET -> Root / "hello" / IntVar(busStop) =>
        for {
          resp <- helloBusClient.hello(BusRequest("27", busStop))
          restResp <- resp match {
            case r: NoBus      => NotFound(r)
            case r: Invalid    => BadRequest(r)
            case r: Successful => Ok(r.buses)
          }
        } yield restResp

      case GET -> Root / "hello" / "" =>
        BadRequest(Invalid("missing busStop path"))

      case GET -> Root / "hello" / invalid =>
        BadRequest(Invalid(s"Invalid busStop: $invalid"))
    }
    .orNotFound
}

object TperBoBotApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val routes: Resource[IO, Routes] = BlazeClientBuilder[IO](global)
      .withConnectTimeout(5 seconds)
      .withRequestTimeout(5 seconds)
      .resource
      .map(client => HelloBusClient(client))
      .map(tperClient => new Routes(tperClient))

    routes
      .use(routes => {
        val app = routes.helloBusService
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
