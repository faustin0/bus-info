import java.time.LocalTime
import java.util.concurrent.Executors

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{Blocker, IO}
import models.{BusRequest, HelloBusResponse, Invalid, NoBus}
import org.http4s.client.{Client, JavaNetClientBuilder}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

class HelloBusClientTest extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  def createFixture = {
    val blockingEC =
      ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
    val blocker = Blocker.liftExecutorService(blockingEC)
    val httpClient: Client[IO] = JavaNetClientBuilder[IO](blocker).create
    HelloBusClient(httpClient)
  }

  "should execute sample tper request " in {

    val sut = createFixture
    val actual = sut.hello(BusRequest(303, "27"))

    actual.asserting {
      case _: HelloBusResponse => succeed
      case _                   => fail()
    }
  }

  "should execute sample tper request barrato" in {
    val sut = createFixture
    val actual =
      sut.hello(BusRequest(303, "85/", LocalTime.of(20, 20)))

    actual.asserting {
      case r: NoBus => succeed
      case _        => fail()
    }
  }

  "should get Invalid when malformed bus" in {
    val sut = createFixture
    val actual = sut.hello(BusRequest(303, "x"))

    actual.asserting {
      case Invalid(_)   => succeed
      case t: Throwable => fail(t)
    }
  }
}
