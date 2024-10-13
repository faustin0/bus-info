package dev.faustin0.api

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import dev.faustin0.HelloBusClient
import dev.faustin0.repositories.DynamoBusStopRepository
import feral.lambda._
import feral.lambda.events._
import feral.lambda.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{ AutoSlash, Logger, Timeout }
import org.http4s.{ HttpRoutes, _ }

import scala.concurrent.duration.DurationInt

class Http4sHandler extends IOLambda[ApiGatewayProxyEventV2, ApiGatewayProxyStructuredResultV2] {

  /** Actually, this is a `Resource` that builds your handler. The handler is acquired exactly once when your Lambda
    * starts and is permanently installed to process all incoming events.
    *
    * The handler itself is a program expressed as `IO[Option[Result]]`, which is run every time that your Lambda is
    * triggered. This may seem counter-intuitive at first: where does the event come from? Because accessing the event
    * via `LambdaEnv` is now also an effect in `IO`, it becomes a step in your program.
    */
  def handler: Resource[IO, ApiGatewayProxyInvocationV2[IO] => IO[Option[ApiGatewayProxyStructuredResultV2]]] =
    myRoutes.map { routes => implicit invocation =>
      // a "middleware" that converts an HttpRoutes into a ApiGatewayProxyHandler
      ApiGatewayProxyHandlerV2[IO](routes.orNotFound)
    }

  def myRoutes: Resource[IO, HttpRoutes[IO]] =
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
      .map(middlewares(_))

  private def endpoints: Resource[IO, Endpoints] =
    (EmberClientBuilder.default[IO].build, DynamoBusStopRepository.makeResource()).parMapN {
      case (emberClient, busStopRepo) =>
        val tperClient     = HelloBusClient.withLogging(emberClient)
        val busInfoService = BusInfoService(tperClient, busStopRepo)
        Endpoints(busInfoService)
    }

  private def middlewares: HttpRoutes[IO] => HttpRoutes[IO] =
    (AutoSlash.httpRoutes(_: HttpRoutes[IO])).andThen {
      Timeout.httpRoutes(
        10.seconds,
        IO(Response[IO](Status.GatewayTimeout).withEntity("TPER server timed out")) // todo chagne timeout location
      )
    } andThen {
      Logger.httpRoutes[IO](logHeaders = true, logBody = false)
    }

}
