import cats.effect.{ Blocker, ContextShift, IO, Resource, Timer }
import com.dimafeng.testcontainers.{ DynaliteContainer, MockServerContainer }
import org.http4s.{ Headers, MediaType, Request, Uri }
import org.http4s.client.JavaNetClientBuilder
import org.http4s.headers.Accept
import org.mockserver.client.MockServerClient
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import repositories.BusStopRepository

import scala.concurrent.ExecutionContext

object Resources {

  def httpClient(blocker: Blocker)(implicit cs: ContextShift[IO]) = JavaNetClientBuilder[IO](blocker).create

  val dynamoDB: DynaliteContainer = DynaliteContainer()

  val mockServer: MockServerContainer = MockServerContainer("5.11.2").configure { c =>
    c.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("ApplicationIT")))
      .waitingFor(Wait.forLogMessage(".*started on port:.*", 1))
  }

  val mockServerClient: Resource[IO, MockServerClient] = Resource
    .fromAutoCloseable(
      IO(new MockServerClient(mockServer.host, mockServer.container.getServerPort))
    )

  def apiServer(ec: ExecutionContext)(implicit cs: ContextShift[IO], timer: Timer[IO]): Resource[IO, Application] =
    HelloBusClient
      .make(mockServer.container.getEndpoint, ec)
      .map(client =>
        BusInfoService(
          client,
          BusStopRepository(dynamoDB.client)
        )
      )
      .map(ser => Application(ser)(ec))


  def resources(ec: ExecutionContext, blocker: Blocker)(implicit cs: ContextShift[IO], timer: Timer[IO]) = for {
    mc  <- mockServerClient
    api <- apiServer(ec)
  } yield (mc, api)

}
