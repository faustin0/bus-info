package dev.faustin0

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.dimafeng.testcontainers.{ ForAllTestContainer, GenericContainer }
import dev.faustin0.Utils.JavaFutureOps
import dev.faustin0.domain.{ BusStop, Position }
import dev.faustin0.repositories.DynamoBusStopRepository
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class DynamoBusStopRepositoryIT
    extends AsyncFreeSpec
    with ForAllTestContainer
    with Matchers
    with AsyncIOSpec
    with Containers {

  override val container: GenericContainer = dynamoContainer

  //TODO remove me once this is solved https://github.com/typelevel/cats-effect-testing/issues/145
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def afterStart(): Unit =
    Containers
      .createDynamoClient(container)
      .use { dynamoClient =>
        for {
          _      <- IO(dynamoClient.createTable(DynamoSetUp.BusStopTable.createTableRequest)).fromCompletable
          tables <- IO(dynamoClient.listTables()).fromCompletable
        } yield tables
      }
      .map(t => assume(!t.tableNames().isEmpty, "dynamo should have tables"))
      .void
      .unsafeRunSync()

  "create and retrieve busStop" in {

    val starting = BusStop(
      0,
      "stop1",
      "PIAZZA MEDAGLIE D`ORO (PENSILINA A)",
      "Bologna",
      42,
      Position(1, 2, 2, 3)
    )

    Containers
      .createDynamoClient(container)
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

    Containers
      .createDynamoClient(container)
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

    Containers
      .createDynamoClient(container)
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
