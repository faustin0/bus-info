import cats.effect.{ContextShift, IO, Resource, Timer}
import models.{BusRequest, Invalid, Successful}
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

import scala.concurrent.ExecutionContext.global
import scala.xml.Elem


class HelloBusClientTest extends FunSuite with BeforeAndAfter with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  var sut: Resource[IO, HelloBusClient] = _

  before {
    sut = BlazeClientBuilder[IO](global)
      .resource
      .map(client => HelloBusClient(client))
  }

  test("should execute sample request ") {
    val actual: String = sut.use(
      client => client.sample()
    ).unsafeRunSync()

    actual.shouldNot(have size 0)
  }


  test("should execute sample tper request ") {
    val actual = sut.use(
      client => client.hello(BusRequest("27", 303))
    ).unsafeRunSync()

    println(actual)
    actual should not be a[Invalid]
  }

  test("should get Invalid when malformed bus") {
    val actual = sut.use(
      client => client.hello(BusRequest("x", 303))
    ).unsafeRunSync()

    println(actual)
    actual shouldBe a[Invalid]
  }

}
