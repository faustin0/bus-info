import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ Blocker, IO }
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.model.{ Projection, ProjectionType, ProvisionedThroughput }
import com.dimafeng.testcontainers.{ DynaliteContainer, ForAllTestContainer, MockServerContainer, MultipleContainers }
import io.circe.Json
import models.{ BusStop, Position }
import org.http4s.{ MediaType, Uri }
import org.http4s.circe.jsonDecoder
import org.http4s.client.JavaNetClientBuilder
import org.http4s.client.dsl.io._
import org.http4s.dsl.io.GET
import org.http4s.headers._
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.freespec.AsyncFreeSpec
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import repositories.{ BusStopEntity, BusStopRepository }

import scala.concurrent.duration.DurationInt

class ApplicationIT extends AsyncFreeSpec with ForAllTestContainer with AsyncIOSpec {

  private val dynamoDB: DynaliteContainer = DynaliteContainer()

  private val mockServer: MockServerContainer = MockServerContainer("5.11.2").configure { c =>
    c.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("ApplicationIT")))
      .waitingFor(Wait.forLogMessage(".*started on port:.*", 1))
  }

  val blocker    = Blocker.liftExecutionContext(executionContext)
  val httpClient = JavaNetClientBuilder[IO](blocker).create

  override def afterStart(): Unit = {
    val mapper       = new DynamoDBMapper(dynamoDB.client)
    val tableRequest = mapper.generateCreateTableRequest(classOf[BusStopEntity])
    tableRequest
      .withProvisionedThroughput(
        new ProvisionedThroughput()
          .withReadCapacityUnits(5)
          .withWriteCapacityUnits(5)
      )
      .getGlobalSecondaryIndexes
      .forEach(gsi =>
        gsi
          .withProvisionedThroughput(new ProvisionedThroughput(5, 5))
          .setProjection(new Projection().withProjectionType(ProjectionType.ALL))
      )

    new DynamoDB(dynamoDB.client)
      .createTable(tableRequest)
      .waitForActive()

    super.afterStart()
  }

  override val container = MultipleContainers(dynamoDB, mockServer)

  "spin container" in {

    val tperStub   = IO {
      new MockServerClient(mockServer.host, mockServer.container.getServerPort)
        .when(
          request()
//            .withPath("web-services/hello-bus.asmx/QueryHellobus")
//            .withMethod("POST")
//            .withQueryStringParameter("fermata", "303")
//            .withQueryStringParameter("linea", "")
//            .withQueryStringParameter("oraHHMM", "")
        )
        .respond(
          response()
            .withBody(
              <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
              TperHellobus: 28 DaSatellite 09:07 (Bus5517 CON PEDANA), 28 DaSatellite 09:20 (Bus5566 CON PEDANA)
            </string>.mkString
            )
        )
    }
    val repository = BusStopRepository(dynamoDB.client)
    val insert     = repository.insert(BusStop(303, "", "", "", 500, Position(0, 0, 0, 0)))

    val app: IO[Unit] = HelloBusClient
      .make(mockServer.container.getEndpoint, executionContext)
      .map(client =>
        BusInfoService(
          client,
          repository
        )
      )
      .map(ser => Application(ser)(executionContext))
      .use(_.run)

    val getRequest = GET(
      Uri.unsafeFromString("http://localhost/bus-stops/303"),
      Accept(MediaType.application.json)
    )

    val r = httpClient.expect[Json](getRequest)

    val actual = for {
      _     <- tperStub
      _     <- insert
      fiber <- app.start
      _     <- IO.sleep(5 seconds)
      resp  <- r.timeout(60 seconds).guarantee {
                 println("cioa")
                 fiber.cancel
               }
    } yield resp

    actual.asserting(json => assert(json.noSpaces.nonEmpty))
  }
}
