package dev.faustin0.runtime

import cats.MonadThrow
import cats.effect.std.Env
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.implicits.http4sLiteralsSyntax

trait LambdaRuntimeEnv[F[_]] {

  /** The name of the function.
    */
  def lambdaFunctionName: F[String]

  /** The amount of memory available to the function in MB.
    */
  def lambdaFunctionMemorySize: F[Int]

  /** The version of the function being executed.
    */
  def lambdaFunctionVersion: F[String]

  /** The name of the Amazon CloudWatch Logs group for the function.
    */
  def lambdaLogGroupName: F[String]

  /** The name of the Amazon CloudWatch Logs stream for the function.
    */
  def lambdaLogStreamName: F[String]

  /** The host and port of the runtime API.
    */
  def lambdaRuntimeApi: F[Uri]
}

object LambdaRuntimeEnv {
  final private[runtime] val AWS_LAMBDA_FUNCTION_NAME        = "AWS_LAMBDA_FUNCTION_NAME"
  final private[runtime] val AWS_LAMBDA_FUNCTION_MEMORY_SIZE = "AWS_LAMBDA_FUNCTION_MEMORY_SIZE"
  final private[runtime] val AWS_LAMBDA_FUNCTION_VERSION     = "AWS_LAMBDA_FUNCTION_VERSION"
  final private[runtime] val AWS_LAMBDA_LOG_GROUP_NAME       = "AWS_LAMBDA_LOG_GROUP_NAME"
  final private[runtime] val AWS_LAMBDA_LOG_STREAM_NAME      = "AWS_LAMBDA_LOG_STREAM_NAME"
  final private[runtime] val AWS_LAMBDA_RUNTIME_API          = "AWS_LAMBDA_RUNTIME_API"

  def apply[F[_]](implicit lre: LambdaRuntimeEnv[F]): LambdaRuntimeEnv[F] = lre

  implicit def forEnv[F[_]: MonadThrow: Env]: LambdaRuntimeEnv[F] =
    new LambdaRuntimeEnv[F] {

      def lambdaFunctionName: F[String] = getOrRaise(AWS_LAMBDA_FUNCTION_NAME)

      def lambdaFunctionMemorySize: F[Int] =
        getOrRaise(AWS_LAMBDA_FUNCTION_MEMORY_SIZE).flatMap(value =>
          value.toIntOption.liftTo(new NumberFormatException(value))
        )

      def lambdaFunctionVersion: F[String] = getOrRaise(AWS_LAMBDA_FUNCTION_VERSION)

      def lambdaLogGroupName: F[String] = getOrRaise(AWS_LAMBDA_LOG_GROUP_NAME)

      def lambdaLogStreamName: F[String] = getOrRaise(AWS_LAMBDA_LOG_STREAM_NAME)

      def lambdaRuntimeApi: F[Uri] =
        getOrRaise(AWS_LAMBDA_RUNTIME_API).flatMap(host => Uri.fromString(s"http://$host").liftTo[F])

      def getOrRaise(envName: String): F[String] =
        Env[F].get(envName).flatMap(_.liftTo(new NoSuchElementException(envName)))
    }

}
