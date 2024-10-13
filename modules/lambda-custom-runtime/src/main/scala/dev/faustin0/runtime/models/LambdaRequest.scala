package dev.faustin0.runtime.models

import cats.effect.Concurrent
import cats.syntax.all._
import dev.faustin0.runtime.ContainerError
import dev.faustin0.runtime.headers.{
  `Lambda-Runtime-Aws-Request-Id`,
  `Lambda-Runtime-Deadline-Ms`,
  `Lambda-Runtime-Invoked-Function-Arn`,
  `Lambda-Runtime-Trace-Id`
}
import io.circe.Json
import org.http4s.circe.jsonDecoderIncremental
import org.http4s.{ EntityDecoder, Response, Status }

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

final private[runtime] class LambdaRequest(
  val deadlineTime: FiniteDuration,
  val id: String,
  val invokedFunctionArn: String,
  val traceId: Option[String],
  val body: Json
)

private[runtime] object LambdaRequest {

  def fromResponse[F[_]](response: Response[F])(implicit F: Concurrent[F]): F[LambdaRequest] =
    response.status match {
      case Status.Ok                  => fromOk(response)
      case Status.InternalServerError => ContainerError.raiseError
      case _                          => response.as[ErrorResponse].flatMap(_.raiseError)
    }

  private def fromOk[F[_]: Concurrent](response: Response[F]): F[LambdaRequest] = {
    implicit val jsonDecoder: EntityDecoder[F, Json] = jsonDecoderIncremental
    for {
      headers                                            <- headersFrom(response).liftTo[F]
      (id, invokedFunctionArn, deadlineTimeInMs, traceId) =
        headers
      body                                               <- response.as[Json]
    } yield new LambdaRequest(
      FiniteDuration(deadlineTimeInMs.value, TimeUnit.MILLISECONDS),
      id.value,
      invokedFunctionArn.value,
      traceId.map(_.value),
      body
    )
  }

  private def headersFrom[F[_]](response: Response[F]) = (
    response.headers
      .get[`Lambda-Runtime-Aws-Request-Id`]
      .toRightNec(`Lambda-Runtime-Aws-Request-Id`.name.toString),
    response.headers
      .get[`Lambda-Runtime-Invoked-Function-Arn`]
      .toRightNec(`Lambda-Runtime-Invoked-Function-Arn`.name.toString),
    response.headers
      .get[`Lambda-Runtime-Deadline-Ms`]
      .toRightNec(`Lambda-Runtime-Deadline-Ms`.name.toString),
    response.headers.get[`Lambda-Runtime-Trace-Id`].rightNec
  ).parTupled.left
    .map(keys => new NoSuchElementException(s"${keys.intercalate(", ")} not found in headers"))

}
