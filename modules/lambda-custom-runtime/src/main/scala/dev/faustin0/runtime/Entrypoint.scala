package dev.faustin0.runtime

import cats.effect.{ IO, IOApp }
import io.circe.Json

object Entrypoint extends IOApp.Simple {

  override def run: IO[Unit] = LambdaRuntime[IO, Json, Json](???)(
    ???
  ) // questo verrà invocato  nel modulo corrispetivo che setterà i tipi del json

}
