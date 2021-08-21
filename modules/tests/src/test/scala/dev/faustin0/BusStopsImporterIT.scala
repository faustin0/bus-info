package dev.faustin0

import cats.effect.IO
import cats.effect.testing.scalatest.{ AssertingSyntax, AsyncIOSpec }
import com.dimafeng.testcontainers.{ ForEachTestContainer, GenericContainer }
import dev.faustin0.domain.{ BusStop, Position }
import dev.faustin0.importer.Importer
import dev.faustin0.importer.domain.{ BusStopsDataset, DatasetFileLocation, Failure, Success }
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
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def afterStart(): Unit =
    Containers
      .createDynamoClient(dynamoContainer)
      .use { ddb =>
        IO.fromCompletableFuture {
          IO(ddb.createTable(DynamoSetUp.BusStopTable.createTableRequest))
        }
      }
      .void
      .unsafeRunSync()

  "should insert all entries when no one exist" in {
    val bucketName                       = "bus-stops"
    val (dataset, busStopsEntriesNumber) = getTestDataset

    val datasetLoader = dataset
      .map(new InMemoryDatasetLoader(bucketName, _))

    Containers
      .createDynamoClient(dynamoContainer)
      .map(DynamoBusStopRepository(_))
      .use { busStopRepo =>
        for {
          ds          <- datasetLoader
          sut          = new Importer(busStopRepo, ds)
          computation <- sut.importFrom(DatasetFileLocation("bus-stops", "bus-stop-dataset.xml"))

        } yield computation
      }
      .asserting {
        case Success(_, processedItems) =>
          assert(processedItems === busStopsEntriesNumber)
        case Failure(_, _, _)           => fail()
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

    val bucketName                       = "bus-stops"
    val (dataset, busStopsEntriesNumber) = getTestDataset

    val datasetLoader = dataset
      .map(new InMemoryDatasetLoader(bucketName, _))

    Containers
      .createDynamoClient(dynamoContainer)
      .map(DynamoBusStopRepository(_))
      .use { busStopRepo =>
        for {
          _           <- Stream
                           .emits(existingBusStops ++ modifiedBusStops)
                           .through(busStopRepo.batchInsert)
                           .compile
                           .drain
          ds          <- datasetLoader
          sut          = new Importer(busStopRepo, ds)
          computation <- sut.importFrom(DatasetFileLocation("bus-stops", "bus-stop-dataset.xml"))
        } yield computation
      }
      .asserting {
        case Success(_, processedItems) =>
          assert(processedItems === busStopsEntriesNumber - existingBusStops.size)
        case Failure(_, _, _)           => fail()
      }
  }

  private def getTestDataset: (IO[BusStopsDataset], Int) = {
    val busStopsEntriesNumber = 31
    IO(getClass.getClassLoader.getResource("bus-stop-dataset.xml"))
      .flatMap(f => IO(xml.XML.load(f)))
      .map(xml => BusStopsDataset("bus-stop-dataset.xml", xml)) -> busStopsEntriesNumber
  }
}
