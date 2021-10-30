package dev.faustin0.api

import cats.effect.kernel.Resource
import cats.effect.{ ExitCode, IO, IOApp }
import cats.implicits._
import dev.faustin0.HelloBusClient
import dev.faustin0.repositories.DynamoBusStopRepository
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.middleware.{ AutoSlash, Logger, Timeout }
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object BusInfoApp extends IOApp {
  private val cachedEc = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  implicit private val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] =
    endpoints
      .map(e =>
        Router(
          "/" -> (
            e.nextBusRoutes <+>
              e.busStopInfoRoutes <+>
              e.busStopSearchRoutes <+>
              e.swaggerUIRoutes
          ),
          "/" -> e.healthCheckRoutes
        )
      )
      .map(routes => middlewares(routes))
      .use(app =>
        BlazeServerBuilder[IO]
          .bindHttp(80, "0.0.0.0")
          .withHttpApp(app)
          .serve
          .compile
          .drain
      )
      .as(ExitCode.Success)

  private val endpoints: Resource[IO, Endpoints] = {
    for {
      tperClient    <- HelloBusClient.make(cachedEc)
      busStopRepo   <- DynamoBusStopRepository.makeResource
      busInfoService = BusInfoService(tperClient, busStopRepo)
      endpoints      = Endpoints(busInfoService)
    } yield endpoints
  }

  private val middlewares: HttpRoutes[IO] => HttpApp[IO] = { http: HttpRoutes[IO] =>
    AutoSlash.httpRoutes(http)
  }.andThen { http =>
    Timeout(
      10.seconds,
      IO(
        Response[IO](Status.GatewayTimeout)
          .withEntity("TPER server timed out")
      )
    )(http.orNotFound)
  }.andThen { http =>
    Logger.httpApp[IO](logHeaders = true, logBody = false, logAction = Some(logger.info(_)))(http)
  }

}
