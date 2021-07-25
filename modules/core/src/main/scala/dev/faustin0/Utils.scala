package dev.faustin0

import cats.effect.IO

import java.util.concurrent.{ CancellationException, CompletableFuture }
import cats.effect.Spawn

object Utils {

  implicit class JavaFutureOps[T](val unevaluatedCF: IO[CompletableFuture[T]]) extends AnyVal {

    def fromCompletable: IO[T] = {
      val computation: IO[T] = unevaluatedCF.flatMap { cf =>
        IO.cancelable { callback =>
          cf.handle((res: T, err: Throwable) =>
            err match {
              case null                     => callback(Right(res))
              case _: CancellationException => ()
              case ex                       => callback(Left(ex))
            }
          )
          //Cancellation token is an effectful action that is able to cancel a running task.
          IO(cf.cancel(true)).void
        }
      }
      computation.guarantee(Spawn[IO].cede)
    }
  }
}
