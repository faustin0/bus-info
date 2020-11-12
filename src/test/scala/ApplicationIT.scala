import cats.effect.testing.scalatest.AsyncIOSpec
import com.dimafeng.testcontainers.{ ForAllTestContainer, MockServerContainer }
import org.scalatest.freespec.AsyncFreeSpec
import org.testcontainers.containers.wait.strategy.Wait

class ApplicationIT extends AsyncFreeSpec with ForAllTestContainer with AsyncIOSpec {

  def container: MockServerContainer = MockServerContainer("5.11.2")
    .configure(c =>
      c
        .withExposedPorts(80)
        .withLogConsumer(f => println(f.getUtf8String))
        .setWaitStrategy(Wait.forLogMessage(".*started on port:.*", 1))
    )

  "spin container" in {

//    new MockServerClient(container.host, container.mappedPort(80))
//      .when(
//        request()
//          .withPath("/person")
//          .withQueryStringParameter("name", "peter")
//      )
//      .respond(
//        response()
//          .withBody("Peter the person!")
//      )

//    HelloBusClient.make(container.host, )
    println(container.container.getContainerId)
    println(container.container.getEndpoint)
    assert(true)
  }
}
