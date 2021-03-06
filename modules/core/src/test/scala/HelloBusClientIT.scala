import java.time.LocalTime
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ Blocker, IO }
import dev.faustin0.HelloBusClient
import dev.faustin0.domain._
import org.http4s.client.JavaNetClientBuilder
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class HelloBusClientIT extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  val blocker    = Blocker.liftExecutionContext(executionContext)
  val httpClient = JavaNetClientBuilder[IO](blocker).create
  val sut        = HelloBusClient(httpClient)

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
