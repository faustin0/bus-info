package dev.faustin0.runtime.models

final case class Invocation[F[_], Event](
  event: Event,
  context: Context[F]
)
