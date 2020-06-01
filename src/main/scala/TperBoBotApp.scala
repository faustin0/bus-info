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
import org.http4s.scalaxml._


class Routes(helloBusClient: HelloBusClient) {
  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" => {
      val resp = helloBusClient.hello(BusRequest("27", 303))
      Ok(resp) //fix me decoder implicit
    }

    case GET -> Root / "test" => Ok("porco dio")
  }.orNotFound
}


object TperBoBotApp extends IOApp {

  //  val helloWorldService = HttpRoutes.of[IO] {
  //    case GET -> Root / "hello" / name => Ok()
  //  }.orNotFound
  //  BlazeClientBuilder[IO](global).resource.use { client: Client[IO] =>
  //    val helloJames = client.expect[String]("http://localhost:8080/hello/James")
  //    // use `client` here and return an `IO`.
  //    // the client will be acquired and shut down
  //    // automatically each time the `IO` is run.
  //    IO.unit
  //  }

  override def run(args: List[String]): IO[ExitCode] = {
    val routes: Resource[IO, Routes] = BlazeClientBuilder[IO](global)
      .withConnectTimeout(5 seconds)
      .resource
      .map(client => HelloBusClient(client))
      .map(tperClient => new Routes(tperClient))

    routes.use(routes => BlazeServerBuilder[IO](global)
      .bindHttp(8080, "localhost")
      .withHttpApp(Logger.httpApp(logHeaders = false, logBody = true)(routes.helloWorldService))
      .serve
      .compile
      .drain
    ).as(ExitCode.Success)

    //    Logger(
    //      logHeaders = false,
    //      logBody = true,
    //      routes.helloWorldService
    //    )

    //    val comps = for {
    //      tperclient <- BlazeClientBuilder[IO](global).resource.use { client =>
    //        // use `client` here and return an `IO`.
    //        // the client will be acquired and shut down
    //        // automatically each time the `IO` is run.
    //        IO.pure(HelloBusClient(client))
    //      }
    //      routes <- IO.pure(new Routes(tperclient))
    //      _ <- BlazeServerBuilder[IO](global)
    //        .bindHttp(8080, "localhost")
    //        .withHttpApp(routes.helloWorldService)
    //        .serve
    //        .compile
    //        .drain
    //    } yield ()

    //    comps.as(ExitCode.Success)
  }
}