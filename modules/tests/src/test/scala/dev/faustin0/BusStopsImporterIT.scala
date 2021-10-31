package dev.faustin0

import cats.effect.IO
import cats.effect.testing.scalatest.{ AssertingSyntax, AsyncIOSpec }
import com.dimafeng.testcontainers.{ ForEachTestContainer, GenericContainer }
import dev.faustin0.Utils.JavaFutureOps
import dev.faustin0.domain.{ BusStop, Position }
import dev.faustin0.importer.Importer
import dev.faustin0.importer.domain.{ DatasetFileLocation, Failure, Success }
import dev.faustin0.repositories.DynamoBusStopRepository
import fs2._
import org.scalatest.freespec.AsyncFreeSpec

class BusStopsImporterIT
    extends AsyncFreeSpec
    with AsyncIOSpec
    with ForEachTestContainer
    with AssertingSyntax
    with Containers {

  override val container: GenericContainer = dynamoContainer
  //TODO remove me once this is solved https://github.com/typelevel/cats-effect-testing/issues/145
  implicit override def executionContext   = scala.concurrent.ExecutionContext.Implicits.global

  override def afterStart(): Unit =
    Containers
      .createDynamoClient(dynamoContainer)
      .use { ddb =>
        IO(ddb.createTable(DynamoSetUp.BusStopTable.createTableRequest)).fromCompletable
      }
      .void
      .unsafeRunSync()

  "should insert all entries when no one exist" in {
    val bucketName = "bus-stops"

    Containers
      .createDynamoClient(dynamoContainer)
      .map(DynamoBusStopRepository(_))
      .use { busStopRepo =>
        val datasetLoader = new InMemoryDatasetLoader(bucketName)
        val sut           = new Importer(busStopRepo, datasetLoader)
        for {
          computation   <- sut.importFrom(DatasetFileLocation("bus-stops", "bus-stop-dataset.xml"))
          expectedCount <- datasetLoader.getBusStopsEntriesNumber
        } yield (computation, expectedCount)
      }
      .asserting {
        case (Success(_, processedItems), expectedCount) =>
          assert(processedItems === expectedCount)
        case (Failure(_, _, _), _)                       => fail()
      }
  }

  "should insert all entries skipping existing ones but updating modified" in {
    val modifiedBusStops = List(
      BusStop(
        code = 1,
        name = "STAZIONE CENTRALE",
        location = "A BRAND NEW LOCATION",
        comune = "BOLOGNA",
        areaCode = 500,
        position = Position(x = 686344, y = 930918, lat = 44.505762, long = 11.343174)
      )
    )
    val existingBusStops = List(
      BusStop(
        code = 10,
        name = "PORTA GALLIERA",
        location = "VIALE MASINI FR 4",
        comune = "BOLOGNA",
        areaCode = 500,
        position = Position(x = 686604, y = 930779, lat = 44.504445, long = 11.346392)
      ),
      BusStop(
        code = 20,
        name = "FILOPANTI",
        location = "VIALE FILOPANTI PALO LUCE 398",
        comune = "BOLOGNA",
        areaCode = 500,
        position = Position(x = 687483, y = 929806, lat = 44.495465, long = 11.357088)
      )
    )

    val bucketName = "bus-stops"

    Containers
      .createDynamoClient(dynamoContainer)
      .map(DynamoBusStopRepository(_))
      .use { busStopRepo =>
        for {
          _ <- Stream
                 .emits(existingBusStops ++ modifiedBusStops)
                 .through(busStopRepo.batchInsert)
                 .compile
                 .drain

          datasetLoader = new InMemoryDatasetLoader(bucketName)
          sut           = new Importer(busStopRepo, datasetLoader)

          computation   <- sut.importFrom(DatasetFileLocation("bus-stops", "bus-stop-dataset.xml"))
          expectedCount <- datasetLoader.getBusStopsEntriesNumber
        } yield (computation, expectedCount)
      }
      .asserting {
        case (Success(_, processedItems), expectedCount) =>
          assert(processedItems === expectedCount - existingBusStops.size)
        case (Failure(_, _, _), _)                       => fail()
      }
  }

}
