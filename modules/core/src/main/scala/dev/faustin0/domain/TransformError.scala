package dev.faustin0.domain

final case class TransformError(
  error: String,
  cause: Option[Throwable] = None
) extends IllegalArgumentException(error, cause.orNull)
