package dev.faustin0.runtime

import cats.effect.{ IO, Ref }
import cats.syntax.all._

class InvocationQuota(ref: Ref[IO, Int], expectedInvocations: Int) {
  def increment: IO[Unit] = ref.update(_ + 1)

  def ensureIsReached: IO[Unit] =
    ref.get
      .ensureOr(i => new IllegalStateException(s"Expected $expectedInvocations invocation, was " + i))(
        _ == expectedInvocations
      )
      .void

}

object InvocationQuota {

  def make(expectedQuota: Int): IO[InvocationQuota] =
    Ref[IO].of(0).map(new InvocationQuota(_, expectedQuota))

}
