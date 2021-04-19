package dev.faustin0.repositories

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ IO, Resource }
import com.dimafeng.testcontainers.{ ForAllTestContainer, GenericContainer }
import dev.faustin0.Utils.JavaFutureOps
import dev.faustin0.domain.{ BusStop, Position }
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.auth.credentials.{ AwsBasicCredentials, StaticCredentialsProvider }
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import java.net.URI

class DynamoBusStopRepositoryIT extends AsyncFreeSpec with ForAllTestContainer with Matchers with AsyncIOSpec {
  implicit private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private lazy val dynamoContainer = GenericContainer(
    dockerImage = "amazon/dynamodb-local",
    exposedPorts = Seq(8000),
    command = Seq("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory"),
    waitStrategy = Wait.forLogMessage(".*CorsParams:.*", 1)
  ).configure { provider =>
    provider.withLogConsumer(t => logger.debug(t.getUtf8String).unsafeRunAsyncAndForget())
    ()
  }

  private def createDynamoClient(): Resource[IO, DynamoDbAsyncClient] = {
    lazy val dynamoDbEndpoint =
      s"http://${dynamoContainer.container.getHost}:${dynamoContainer.container.getFirstMappedPort}"

    Resource.fromAutoCloseable {
      IO(
        DynamoDbAsyncClient
          .builder()
          .region(Region.EU_CENTRAL_1)
          .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
          .endpointOverride(URI.create(dynamoDbEndpoint))
          .build()
      )
    }
  }

  override val container: GenericContainer = dynamoContainer

  override def afterStart(): Unit = {
    val createTableRequest = CreateTableRequest
      .builder()
      .tableName("bus_stops")
      .attributeDefinitions(
        AttributeDefinition
          .builder()
          .attributeName("code")
          .attributeType(ScalarAttributeType.N)
          .build(),
        AttributeDefinition
          .builder()
          .attributeName("name")
          .attributeType(ScalarAttributeType.S)
          .build()
      )
      .keySchema(
        KeySchemaElement
          .builder()
          .attributeName("code")
          .keyType(KeyType.HASH)
          .build()
      )
      .provisionedThroughput(
        ProvisionedThroughput
          .builder()
          .readCapacityUnits(5)
          .writeCapacityUnits(14)
          .build()
      )
      .globalSecondaryIndexes(
        GlobalSecondaryIndex
          .builder()
          .indexName("name-index")
          .projection(
            Projection
              .builder()
              .projectionType(ProjectionType.ALL)
              .build()
          )
          .keySchema(
            KeySchemaElement
              .builder()
              .attributeName("name")
              .keyType(KeyType.HASH)
              .build()
          )
          .provisionedThroughput(
            ProvisionedThroughput
              .builder()
              .readCapacityUnits(5)
              .writeCapacityUnits(7)
              .build()
          )
          .build()
      )
      .build()

    createDynamoClient().use { client =>
      IO(client.createTable(createTableRequest)).fromCompletable.void
    }.unsafeRunSync()
  }

  "spin container" in {
    createDynamoClient()
      .use(client => IO(client.listTables().get().hasTableNames))
      .asserting(assume(_))
  }

  "create and retrieve busStop" in {

    val starting = BusStop(
      0,
      "stop1",
      "PIAZZA MEDAGLIE D`ORO (PENSILINA A)",
      "Bologna",
      42,
      Position(1, 2, 2, 3)
    )

    createDynamoClient()
      .map(DynamoBusStopRepository(_))
      .use { repo =>
        for {
          _      <- repo.insert(starting)
          actual <- repo.findBusStopByCode(0)
        } yield actual
      }
      .asserting {
        case Some(value) => assert(value === starting)
        case None        => fail()
      }
  }

  "should batch insert entries" in {
    val batchSize = 1000L

    val entry = (code: Int) =>
      BusStop(
        code,
        "stop1",
        "PIAZZA MEDAGLIE D`ORO (PENSILINA A)",
        "Bologna",
        42,
        Position(1, 2, 2, 3)
      )

    val entries = fs2.Stream
      .iterate(0)(i => i + 1)
      .map(c => entry(c))
      .take(batchSize)
      .covary[IO]

    createDynamoClient()
      .map(DynamoBusStopRepository(_))
      .use { repo =>
        for {
          errors <- entries.through(repo.batchInsert).compile.toList
          count  <- repo.count //todo make the counting "isolated" from other tests
        } yield (count, errors)
      }
      .asserting { case (count, errs) =>
        count shouldBe batchSize
        errs shouldBe empty
      }
  }

  "should retrieve busStops by name" in {
    val stop = BusStop(0, "IRNERIO", "MOCK", "Bologna", 42, Position(1, 2, 2, 3))
    val s303 = stop.copy(code = 303, location = "VIA IRNERIO 1")
    val s304 = stop.copy(code = 304, location = "VIA IRNERIO 2")

    createDynamoClient()
      .map(DynamoBusStopRepository(_))
      .use { repo =>
        for {
          _      <- repo.insert(s303)
          _      <- repo.insert(s304)
          actual <- repo.findBusStopByName("IRNERIO")
        } yield actual
      }
      .asserting(stops => stops should contain.only(s303, s304))
  }
}
