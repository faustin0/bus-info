package dev.faustin0.api

import cats.effect.{ ExitCode, IO, IOApp }
import cats.implicits._
import dev.faustin0.HelloBusClient
import dev.faustin0.repositories.DynamoBusStopRepository
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

object BusInfoApp extends IOApp {
  private val cachedEc = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  implicit private val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    val application = for {
      tperClient    <- HelloBusClient.make(cachedEc)
      busStopRepo   <- DynamoBusStopRepository.makeResource
      busInfoService = BusInfoService(tperClient, busStopRepo)
      endpoints      = Endpoints(busInfoService)
    } yield Router(
      "/" -> (
        endpoints.nextBusRoutes <+>
          endpoints.busStopInfoRoutes <+>
          endpoints.busStopSearchRoutes <+>
          endpoints.swaggerUIRoutes
      ),
      "/" -> endpoints.healthCheckRoutes
    ).orNotFound

    application.use { app =>
      val loggedApp = Logger.httpApp(logHeaders = true, logBody = false)(app)

      BlazeServerBuilder[IO](global)
        .bindHttp(80, "0.0.0.0")
        .withHttpApp(loggedApp)
        .serve
        .compile
        .drain
    }.as(ExitCode.Success)
  }
}
