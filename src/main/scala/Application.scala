import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import cats.implicits.toSemigroupKOps
import org.http4s.HttpApp
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import scala.concurrent.ExecutionContext

class Application private (private val logic: HttpApp[IO], private val ec: ExecutionContext)(implicit
  val cc: ConcurrentEffect[IO],
  val timer: Timer[IO]
) {

  def run: IO[Unit] = {
    val loggedApp = Logger.httpApp(logHeaders = false, logBody = true)(logic)

    BlazeServerBuilder[IO](ec)
      .bindHttp(80, "0.0.0.0")
      .withHttpApp(loggedApp)
      .serve
      .compile
      .drain
  }
}

object Application {

  def apply(
    busInfoService: BusInfoService
  )(ec: ExecutionContext)(implicit cs: ContextShift[IO], timer: Timer[IO]) = new Application(
    {
      val endpoints = Endpoints(busInfoService)
      Router(
        "/" -> (
          endpoints.nextBusRoutes <+>
            endpoints.busStopInfoRoutes <+>
            endpoints.busStopSearchRoutes <+>
            endpoints.swaggerRoutes
        ),
        "/" -> endpoints.healthCheckRoutes
      ).orNotFound
    },
    ec
  )
}
