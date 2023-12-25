import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import dev.faustin0.HelloBusClient
import dev.faustin0.domain._
import org.http4s.client.JavaNetClientBuilder
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.http4s.client.middleware.{ Logger => ClientLogger }
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalTime

class HelloBusClientIT extends AsyncFreeSpec with AsyncIOSpec with Matchers {
  implicit private val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  private val httpClient = ClientLogger(logHeaders = false, logBody = true)(JavaNetClientBuilder[IO].create)
  private val sut        = HelloBusClient(httpClient)

  "should execute sample tper request " in {
    val actual = sut.hello(BusRequest(303, "27"))

    actual.asserting {
      case _: BusInfoResponse => succeed
      case _                  => fail()
    }
  }

  "should execute sample tper request barrato" in {
    val actual = sut.hello(BusRequest(303, "85/", LocalTime.of(20, 20)))

    actual.asserting {
      case Successful(Nil) => succeed
      case _               => fail()
    }
  }

  "should get Invalid when malformed bus" in {
    val actual = sut.hello(BusRequest(303, "x"))

    actual.asserting {
      case BusNotHandled(_) => succeed
      case _                => fail()
    }
  }

  "should get next buses for stop" in {
    val actual = sut.hello(BusRequest(1010))

    actual.asserting {
      case Successful(List(_, _*)) => succeed
      case Failure(error)          => fail(error)
      case _                       => fail()
    }
  }

}
