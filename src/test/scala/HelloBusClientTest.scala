import java.time.LocalTime
import java.util.concurrent.Executors

import cats.effect.{Blocker, ContextShift, IO, Timer}
import models.{BusRequest, Invalid, NoBus}
import org.http4s.client.{Client, JavaNetClientBuilder}
import org.scalatest.{BeforeAndAfter, FunSuite, Inside, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global


class HelloBusClientTest extends FunSuite with BeforeAndAfter with Matchers with Inside {

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  var sut: HelloBusClient = _

  before {
    val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
    val blocker = Blocker.liftExecutorService(blockingEC)
    val httpClient: Client[IO] = JavaNetClientBuilder[IO](blocker).create
    sut = HelloBusClient(httpClient)
  }

  test("should execute sample request ") {
    val actual = sut.sample().unsafeRunSync()
    actual.shouldNot(have size 0)
  }


  test("should execute sample tper request ") {
    val actual = sut.hello(BusRequest("27", 303)).unsafeRunSync()

    println(actual)
    inside(actual) { case Right(response) =>
      response should not be a[Invalid]
    }
  }

  test("should execute sample tper request barrato") {
    val actual = sut.hello(BusRequest("85/", 303, LocalTime.of(20, 20))).unsafeRunSync()

    println(actual)
    inside(actual) { case Right(response) =>
      response should not be a[Invalid]
      response shouldBe a[NoBus]
    }
  }

  test("should get Invalid when malformed bus") {
    val actual = sut.hello(BusRequest("x", 303)).unsafeRunSync()

    println(actual)
    inside(actual) { case Right(response) =>
      response shouldBe a[Invalid]
    }
  }
}
