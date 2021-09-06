package dev.faustin0

import cats.effect.IO

import java.util.concurrent.CompletableFuture

object Utils {

  implicit class JavaFutureOps[T](val unevaluatedCF: IO[CompletableFuture[T]]) extends AnyVal {

    def fromCompletable: IO[T] = IO.fromCompletableFuture(unevaluatedCF)
  }
}
