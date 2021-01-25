import Resources._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ Blocker, IO }
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.model.{ Projection, ProjectionType, ProvisionedThroughput }
import com.dimafeng.testcontainers.{ ForAllTestContainer, MultipleContainers }
import io.circe.Json
import models.{ BusStop, Position }
import org.http4s._
import org.http4s.circe.jsonDecoder
import org.http4s.headers._
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import repositories.{ BusStopEntity, BusStopRepository }

import scala.concurrent.duration.DurationInt

class ApplicationIT extends AsyncFreeSpec with ForAllTestContainer with AsyncIOSpec with Matchers {

  val blocker: Blocker = Blocker.liftExecutionContext(executionContext)

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

  "check 200 status and json response" in {

    val actual = resources(executionContext).use { case (mockServerClient, apiServer) =>
      val req: Request[IO] = Request(
        uri = Uri.unsafeFromString("http://localhost/bus-stops/303"),
        headers = Headers.of(Accept(MediaType.application.json))
      )

      val registerExpectation = IO(
        mockServerClient
          .when(
            request()
            //            .withPath("web-services/hello-bus.asmx/QueryHellobus")
            //            .withMethod("POST")
            //            .withQueryStringParameter("fermata", "303")
            //            .withQueryStringParameter("linea", "")
            //            .withQueryStringParameter("oraHHMM", "")
          )
          .respond(
            response().withBody(
              <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
                TperHellobus: 28 DaSatellite 09:07 (Bus5517 CON PEDANA), 28 DaSatellite 09:20 (Bus5566 CON PEDANA)
              </string>.mkString
            )
          )
      )

      for {
        _        <- registerExpectation
        _        <- BusStopRepository(dynamoDB.client).insert(BusStop(303, "", "", "", 0, Position(0, 0, 0, 0)))
        server   <- apiServer.run.start
        _        <- IO.sleep(5 seconds)
        response <-
          httpClient(blocker).run(req).use(r => r.attemptAs[Json].value.map(j => (r, j))).guarantee(server.cancel)
      } yield response
    }

    actual.asserting(response => assert(response._1.status == Status.Ok))
    actual.asserting(e => e._2.fold(failure => fail(failure), json => json.noSpaces.nonEmpty.shouldBe(true)))
  }
}
