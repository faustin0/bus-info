package dev.faustin0.runtime.models

import cats.effect.Concurrent
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

private[runtime] case class ErrorResponse(
  errorMessage: String,
  errorType: String
) extends Exception

private[runtime] object ErrorResponse {

  implicit val dec: Decoder[ErrorResponse] = deriveDecoder

  implicit def entDec[F[_]: Concurrent]: EntityDecoder[F, ErrorResponse] =
    jsonOf[F, ErrorResponse]

}
