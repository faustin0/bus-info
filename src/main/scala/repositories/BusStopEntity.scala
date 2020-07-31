package repositories

import com.amazonaws.services.dynamodbv2.datamodeling.{DynamoDBAttribute, DynamoDBHashKey, DynamoDBTable}

import scala.annotation.meta.field
import scala.beans.BeanProperty

@DynamoDBTable(tableName = "bus_stops")
case class BusStopEntity(
  @(DynamoDBHashKey @field)
  @(DynamoDBAttribute @field)
  @BeanProperty var code: Long,
  @(DynamoDBAttribute @field)
  @BeanProperty var name: String
) {
  def this() = this(0, "")
}