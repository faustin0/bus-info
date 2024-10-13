package dev.faustin0.runtime.models

import io.circe._
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf

/** identify the response to reply for the reportInitError
  */
final case class ErrorRequest( // todo, response?
  errorMessage: String,
  errorType: String,
  stackTrace: List[String]
)

private[runtime] object ErrorRequest {


  def fromThrowable(ex: Throwable): ErrorRequest =
    ErrorRequest(
      ex.getMessage,
      ex.getClass.getSimpleName,
      ex.getStackTrace().toList.map(_.toString)
    )

  implicit val enc: Encoder[ErrorRequest] = deriveEncoder

  implicit def entEnc[F[_]]: EntityEncoder[F, ErrorRequest] =
    jsonEncoderOf[F, ErrorRequest]

}
