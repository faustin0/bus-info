package dev.faustin0.runtime.models

import cats.MonadThrow
import cats.syntax.all._
import dev.faustin0.runtime.LambdaRuntimeEnv

private[runtime] case class LambdaSettings( // todo check if necessary
  functionName: String,
  functionVersion: String,
  functionMemorySize: Int,
  logGroupName: String,
  logStreamName: String
)

private[runtime] object LambdaSettings {

  def fromLambdaEnv[F[_]: MonadThrow](implicit Env: LambdaRuntimeEnv[F]): F[LambdaSettings] = (
    Env.lambdaFunctionName,
    Env.lambdaFunctionVersion,
    Env.lambdaFunctionMemorySize,
    Env.lambdaLogGroupName,
    Env.lambdaLogStreamName
  ).mapN(LambdaSettings.apply)

}
