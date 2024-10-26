package dev.faustin0.runtime

import cats.data.EitherT
import cats.effect.implicits.effectResourceOps
import cats.effect.{ Resource, Temporal }
import cats.syntax.all._
import dev.faustin0.runtime.models.{ Context, Invocation, LambdaRequest, LambdaSettings }
import io.circe.{ Decoder, Encoder }
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

import scala.util.control.NonFatal

class LambdaRuntime[F[_]: Temporal: Logger](lambdaAPI: LambdaRuntimeAPIClient[F]) {

  def runLoop[Event: Decoder, Result: Encoder](
    settings: LambdaSettings,
    run: Invocation[F, Event] => F[Option[Result]]
  ): F[Unit] =
    lambdaAPI
      .nextInvocation()
      .flatMap(req =>
        handleSingleRequest(settings, run)(req)
          .foldF(ex => lambdaAPI.reportInvocationError(req.id, ex), _ => ().pure)
      )
      .handleErrorWith {
        case ex @ ContainerError => ex.raiseError
        case NonFatal(err)       => Logger[F].warn(err)("Caught error during runtime loop")
        case ex                  => ex.raiseError
      }
      .foreverM // iterator-style blocking API

  private def handleSingleRequest[Event: Decoder, Result: Encoder](
    settings: LambdaSettings,
    run: Invocation[F, Event] => F[Option[Result]]
  )(request: LambdaRequest): EitherT[F, Throwable, Unit] =
    for {
      event       <- EitherT.fromEither[F](request.body.as[Event])
      invocation   = Invocation(event, contextFrom(request, settings))
      maybeResult <- EitherT(run(invocation).attempt)
      _           <- EitherT.liftF(maybeResult.traverse(lambdaAPI.submit(request.id, _)))
    } yield ()

  private def contextFrom(request: LambdaRequest, settings: LambdaSettings): Context[F] =
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
      remainingTime = Temporal[F].realTime.map(request.deadlineTime - _),
      xRayTraceId = request.traceId
    )

}

object LambdaRuntime {

  def apply[F[_]: Temporal: Logger: LambdaRuntimeEnv, Event: Decoder, Result: Encoder](
    client: Client[F]
  )(handler: Resource[F, Invocation[F, Event] => F[Option[Result]]]): F[Unit] =
    LambdaRuntimeAPIClient(client).flatMap(client =>
      (handler, LambdaSettings.fromLambdaEnv.toResource).parTupled.attempt
        .use[Unit] {
          case Right((handler, settings)) => new LambdaRuntime[F](client).runLoop(settings, handler)
          case Left(ex)                   => client.reportInitError(ex) *> ex.raiseError
        }
    )

}
