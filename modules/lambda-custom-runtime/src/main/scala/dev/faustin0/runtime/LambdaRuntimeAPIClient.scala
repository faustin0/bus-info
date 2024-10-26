package dev.faustin0.runtime

import cats.effect.Concurrent
import cats.syntax.all._
import dev.faustin0.runtime.headers.`Lambda-Runtime-Function-Error-Type`
import dev.faustin0.runtime.models.{ ErrorRequest, ErrorResponse, LambdaRequest }
import io.circe.Encoder
import org.http4s.circe.jsonEncoderOf
import org.http4s.client.Client
import org.http4s.{ Method, Request, Response, Status }

/** AWS Lambda Runtime API Client
  */
private[runtime] trait LambdaRuntimeAPIClient[F[_]] {

  /** Non-recoverable initialization error. Runtime should exit after reporting the error. Error will be served in
    * response to the first invoke.
    */
  def reportInitError(t: Throwable): F[Unit]

  /** Runtime makes this request when it is ready to receive and process a new invoke.
    */
  def nextInvocation(): F[LambdaRequest]

  /** Runtime makes this request in order to submit a response.
    */
  def submit[T: Encoder](awsRequestId: String, responseBody: T): F[Unit]

  /** Runtime makes this request in order to submit an error response. It can be either a function error, or a runtime
    * error. Error will be served in response to the invoke.
    */
  def reportInvocationError(awsRequestId: String, t: Throwable): F[Unit]
}

private[runtime] object LambdaRuntimeAPIClient {
  final val ApiVersion = "2018-06-01"

  def apply[F[_]: Concurrent: LambdaRuntimeEnv](
    client: Client[F]
  ): F[LambdaRuntimeAPIClient[F]] = // todo pass only the uri
    LambdaRuntimeEnv[F].lambdaRuntimeApi.map { host =>
      val runtimeApi = host / ApiVersion / "runtime"
      new LambdaRuntimeAPIClient[F] {
        def reportInitError(t: Throwable): F[Unit] =
          client
            .run(
              Request[F]()
                .withMethod(Method.POST)
                .withUri(runtimeApi / "init" / "error")
                .withHeaders(`Lambda-Runtime-Function-Error-Type`("Runtime.UnknownReason"))
                .withEntity(ErrorRequest.fromThrowable(t))
            )
            .use[Unit](handleResponse)

        def nextInvocation(): F[LambdaRequest] =
          client.get(runtimeApi / "invocation" / "next")(LambdaRequest.fromResponse[F])

        def submit[T: Encoder](awsRequestId: String, responseBody: T): F[Unit] =
          client
            .run(
              Request[F]()
                .withMethod(Method.POST)
                .withUri(runtimeApi / "invocation" / awsRequestId / "response")
                .withEntity(responseBody)(jsonEncoderOf)
            )
            .use[Unit](handleResponse)

        def reportInvocationError(awsRequestId: String, t: Throwable): F[Unit] =
          client
            .run(
              Request[F]()
                .withMethod(Method.POST)
                .withUri(runtimeApi / "invocation" / awsRequestId / "error")
                .withEntity(ErrorRequest.fromThrowable(t))
                .withHeaders(`Lambda-Runtime-Function-Error-Type`("Runtime.UnknownReason")) // todo check this reason
            )
            .use[Unit](handleResponse)
      }
    }

  private def handleResponse[F[_]: Concurrent](response: Response[F]): F[Unit] =
    response.status match {
      case Status.InternalServerError => ContainerError.raiseError
      case Status.Accepted            => ().pure
      case _                          => response.as[ErrorResponse].flatMap(_.raiseError)
    }

}
