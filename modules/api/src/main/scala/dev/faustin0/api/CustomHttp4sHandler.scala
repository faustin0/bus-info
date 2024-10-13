package dev.faustin0.api

import cats.effect.kernel.Resource
import cats.effect.{ IO, IOApp }
import cats.syntax.all._
import dev.faustin0.HelloBusClient
import dev.faustin0.repositories.DynamoBusStopRepository
import dev.faustin0.runtime.LambdaRuntime
import feral.lambda._
import feral.lambda.events._
import feral.lambda.http4s._
import org.http4s.client.Client
import org.http4s.client.middleware.{ Logger => ClientLogger }
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{ AutoSlash, Logger, Timeout }
import org.http4s.{ HttpRoutes, _ }
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.DurationInt

object Entrypoint extends IOApp.Simple {

  override def run: IO[Unit] =
    (
      Slf4jLogger.create[IO].toResource,
      EmberClientBuilder
        .default[IO]
        .withTimeout(2.minutes)
        .withIdleTimeInPool(5.minutes)
        .build
    ).tupled.use { case (logger, httpClient) =>
      val loggedRuntimeClient = ClientLogger(
        logHeaders = false,
        logBody = true,
        logAction = Some((str: String) => logger.trace(str))
      )(httpClient)
      val lambdaHandler       = handler(httpClient)
      LambdaRuntime[IO, ApiGatewayProxyEventV2, ApiGatewayProxyStructuredResultV2](loggedRuntimeClient)(lambdaHandler)
    }

  /** Actually, this is a `Resource` that builds your handler. The handler is acquired exactly once when your Lambda
    * starts and is permanently installed to process all incoming events.
    *
    * The handler itself is a program expressed as `IO[Option[Result]]`, which is run every time that your Lambda is
    * triggered. This may seem counter-intuitive at first: where does the event come from? Because accessing the event
    * via `LambdaEnv` is now also an effect in `IO`, it becomes a step in your program.
    */
  def handler(
    client: Client[IO]
  ): Resource[IO, dev.faustin0.runtime.models.Invocation[IO, ApiGatewayProxyEventV2] => IO[
    Option[ApiGatewayProxyStructuredResultV2]
  ]] =
    myRoutes(client).map { routes => implicit invocation =>
      implicit val adaptedInvocation = Invocation.pure(
        invocation.event,
        Context[IO](
          invocation.context.functionName,
          invocation.context.functionVersion,
          invocation.context.invokedFunctionArn,
          invocation.context.memoryLimitInMB,
          invocation.context.awsRequestId,
          invocation.context.logGroupName,
          invocation.context.logStreamName,
          None,
          None,
          invocation.context.remainingTime
        )
      )
      // a "middleware" that converts an HttpRoutes into a ApiGatewayProxyHandler
      ApiGatewayProxyHandlerV2[IO](routes.orNotFound)
    }

  def myRoutes(client: Client[IO]): Resource[IO, HttpRoutes[IO]] =
    endpoints(client)
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

  private def endpoints(client: Client[IO]): Resource[IO, Endpoints] =
    DynamoBusStopRepository.makeResource().map { case busStopRepo =>
      val tperClient     = HelloBusClient.withLogging(client)
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
