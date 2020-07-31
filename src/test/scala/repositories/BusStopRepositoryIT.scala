package repositories

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.dimafeng.testcontainers.{DynaliteContainer, ForAllTestContainer}
import models.{BusStop, Position}
import org.scalatest.funsuite.AnyFunSuite

class BusStopRepositoryIT extends AnyFunSuite with ForAllTestContainer {
  override val container: DynaliteContainer = DynaliteContainer()

  override def afterStart(): Unit = {
    val mapper = new DynamoDBMapper(container.client)
    val tableRequest = mapper.generateCreateTableRequest(new BusStopEntity().getClass)
    tableRequest.setProvisionedThroughput(
      new ProvisionedThroughput()
        .withReadCapacityUnits(5)
        .withWriteCapacityUnits(2)
    )

    val dynamoDB = new DynamoDB(container.client)
    val creating = dynamoDB.createTable(tableRequest)
    creating.waitForActive()

    super.afterStart()
  }

  test("spin container") {
    assert(!container.client.listTables().getTableNames.isEmpty)
  }

  test("create busStop") {
    val repo = new BusStopRepository(container.client)
    repo.insert(BusStop(1, "stop1", "", "", 0, Position(0, 0, 0, 0)))
  }
}
