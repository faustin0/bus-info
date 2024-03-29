//package dev.faustin0.api
//
//import cats.effect.kernel.Resource
//import cats.effect.{ExitCode, IO, IOApp}
//import cats.implicits._
//import com.comcast.ip4s._
//import dev.faustin0.HelloBusClient
//import dev.faustin0.repositories.DynamoBusStopRepository
//import org.http4s._
//import org.http4s.ember.client.EmberClientBuilder
//import org.http4s.ember.server.EmberServerBuilder
//import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
//import org.http4s.server.Router
//import org.http4s.server.middleware.{AutoSlash, Logger, Timeout}
//import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
//import org.typelevel.log4cats.slf4j.Slf4jLogger
//
//import scala.concurrent.duration.DurationInt
//
//object BusInfoApp extends IOApp {
//
////  implicit private val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
//
//  override def run(args: List[String]): IO[ExitCode] = {
//    val app = for {
//      httpApp <- endpoints
//                   .map(e =>
//                     Router(
//                       "/" -> (
//                         e.nextBusRoutes <+>
//                           e.busStopInfoRoutes <+>
//                           e.busStopSearchRoutes <+>
//                           e.swaggerUIRoutes
//                       ),
//                       "/" -> e.healthCheckRoutes
//                     )
//                   )
//                   .map(middlewares(_))
//      _       <- EmberServerBuilder
//                   .default[IO]
//                   .withHost(ip"0.0.0.0")
//                   .withPort(port"80")
//                   .withHttpApp(httpApp)
//                   .build
//    } yield ()
//
//    app.useForever.as(ExitCode.Success)
//  }
//
//  private val endpoints: Resource[IO, Endpoints] =
//    for {
//      logger        <- Slf4jLogger.create[IO].toResource
//      emberClient   <- EmberClientBuilder
//                         .default[IO]
//                         .build
//      busStopRepo   <- DynamoBusStopRepository.makeResource(logger)
//      tperClient     = HelloBusClient(emberClient, logger.info(_))
//      busInfoService = BusInfoService(tperClient, busStopRepo)
//      endpoints      = Endpoints(busInfoService)
//    } yield endpoints
//
//  private def middlewares(l: Logger[IO]): HttpRoutes[IO] => HttpApp[IO] = { http: HttpRoutes[IO] =>
//    AutoSlash.httpRoutes(http)
//  }.andThen { http =>
//    Timeout(
//      10.seconds,
//      IO(Response[IO](Status.GatewayTimeout).withEntity("TPER server timed out"))
//    )(http.orNotFound)
//  }.andThen {
//    Logger.httpApp[IO](logHeaders = true, logBody = false, logAction = Some(logger.info(_)))
//  }
//
//}
