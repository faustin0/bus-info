package repositories

import cats.data.OptionT
import cats.effect.testing.scalatest.AsyncIOSpec
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.model._
import com.dimafeng.testcontainers.{DynaliteContainer, ForAllTestContainer}
import models.{BusStop, Position}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class BusStopRepositoryIT
    extends AsyncFreeSpec
    with ForAllTestContainer
    with Matchers
    with AsyncIOSpec {
  override val container: DynaliteContainer = DynaliteContainer()

  override def afterStart(): Unit = {
    val mapper       = new DynamoDBMapper(container.client)
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

    new DynamoDB(container.client)
      .createTable(tableRequest)
      .waitForActive()

    super.afterStart()
  }

  "spin container" in {
    assume(container.client.listTables().getTableNames.size() > 0)
  }

  "create and retrieve busStop" in {
    val repo = new BusStopRepository(container.client)
    val starting = BusStop(
      0,
      "stop1",
      "PIAZZA MEDAGLIE D`ORO (PENSILINA A)",
      "Bologna",
      42,
      Position(1, 2, 2, 3)
    )

    val actual = for {
      _      <- OptionT.liftF(repo.insert(starting))
      actual <- repo.findBusStopByCode(0)
    } yield actual

    actual.value.asserting {
      case Some(value) => assert(value === starting)
      case None        => fail()
    }
  }

  "should batch insert entries" in {
    val repo = new BusStopRepository(container.client)
    val entry = (code: Long) =>
      BusStop(
        code,
        "stop1",
        "PIAZZA MEDAGLIE D`ORO (PENSILINA A)",
        "Bologna",
        42,
        Position(1, 2, 2, 3)
      )

    val entries = LazyList
      .from(0)
      .map(c => entry(c.toLong))
      .take(1000)
      .toList

    val res = for {
      failures <- repo.batchInsert(entries).compile.toList
      count    <- repo.count()
    } yield (failures, count)

    res asserting {
      case (Nil, 1000) => succeed
      case errs        => fail(errs.toString())
    }
  }

  "should retrieve busStops by name" in {
    val repo = new BusStopRepository(container.client)
    val stop = BusStop(
      0,
      "IRNERIO",
      "MOCK",
      "Bologna",
      42,
      Position(1, 2, 2, 3)
    )
    val s303 = stop.copy(code = 303, location = "VIA IRNERIO 1")
    val s304 = stop.copy(code = 304, location = "VIA IRNERIO 2")

    val actualBusStops = for {
      _      <- repo.insert(s303)
      _      <- repo.insert(s304)
      actual <- repo.findBusStopByName("IRNERIO").compile.toList
    } yield actual

    actualBusStops asserting { stops => stops should contain.only(s303, s304) }
  }
}
