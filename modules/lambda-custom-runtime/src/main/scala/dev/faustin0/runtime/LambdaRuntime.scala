package dev.faustin0.runtime

import cats.effect.implicits.effectResourceOps
import cats.effect.{ Resource, Temporal }
import cats.syntax.all._
import dev.faustin0.runtime.models.{ Context, Invocation, LambdaRequest, LambdaSettings }
import io.circe.{ Decoder, Encoder }
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

import scala.util.control.NonFatal

object LambdaRuntime {

  def apply[F[_]: Temporal: Logger: LambdaRuntimeEnv, Event: Decoder, Result: Encoder](
    client: Client[F]
  )(handler: Resource[F, Invocation[F, Event] => F[Option[Result]]]): F[Unit] =
    LambdaRuntimeAPIClient(client).flatMap(client =>
      (handler, LambdaSettings.fromLambdaEnv.toResource).parTupled.attempt
        .use[Unit] {
          case Right((handler, settings)) => runLoop(client, settings, handler)
          case Left(ex)                   => client.reportInitError(ex) *> ex.raiseError
        }
    )

  private def runLoop[F[_]: Temporal: Logger, Event: Decoder, Result: Encoder](
    client: LambdaRuntimeAPIClient[F],
    settings: LambdaSettings,
    run: Invocation[F, Event] => F[Option[Result]]
  ): F[Unit] =
    Logger[F].trace("Starting runtime loop...") *>
      client
        .nextInvocation()
        .flatMap(handleSingleRequest(client, settings, run))
        .handleErrorWith {
          case ex @ ContainerError => ex.raiseError[F, Unit]
          case NonFatal(_)         => ().pure
          case ex                  => ex.raiseError
        }
        .foreverM

  private def handleSingleRequest[F[_]: Temporal, Event: Decoder, Result: Encoder](
    client: LambdaRuntimeAPIClient[F],
    settings: LambdaSettings,
    run: Invocation[F, Event] => F[Option[Result]]
  )(request: LambdaRequest): F[Unit] = {
    val program = for {
      event       <- request.body.as[Event].liftTo[F]
      maybeResult <- run(Invocation(event, contextFrom[F](request, settings)))
      _           <- maybeResult.traverse(client.submit(request.id, _))
    } yield ()
    program.handleErrorWith {
      case ex @ ContainerError => ex.raiseError
      case NonFatal(ex)        => client.reportInvocationError(request.id, ex)
      case ex                  => ex.raiseError
    }
  }

  def contextFrom[F[_]](request: LambdaRequest, settings: LambdaSettings)(implicit F: Temporal[F]): Context[F] =
    Context[F](
      functionName = settings.functionName,
      functionVersion = settings.functionVersion,
      invokedFunctionArn = request.invokedFunctionArn,
      memoryLimitInMB = settings.functionMemorySize,
      awsRequestId = request.id,
      logGroupName = settings.logGroupName,
      logStreamName = settings.logStreamName,
      identity = None,      // todo
      clientContext = None, // todo
      remainingTime = F.realTime.map(request.deadlineTime - _),
      xRayTraceId = request.traceId
    )

}
