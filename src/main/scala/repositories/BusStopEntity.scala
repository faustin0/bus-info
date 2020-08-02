package repositories

import com.amazonaws.services.dynamodbv2.datamodeling.{
  DynamoDBAttribute,
  DynamoDBHashKey,
  DynamoDBTable
}

import scala.annotation.meta.field
import scala.beans.BeanProperty

@DynamoDBTable(tableName = "bus_stops")
case class BusStopEntity(
  @(DynamoDBHashKey @field)
  @(DynamoDBAttribute @field) @BeanProperty var code: Long,
  @(DynamoDBAttribute @field) @BeanProperty var name: String,
  @(DynamoDBAttribute @field) @BeanProperty var location: String,
  @(DynamoDBAttribute @field) @BeanProperty var comune: String,
  @(DynamoDBAttribute @field) @BeanProperty var areaCode: Int,
  @(DynamoDBAttribute @field) @BeanProperty var lat: Double,
  @(DynamoDBAttribute @field) @BeanProperty var lng: Double,
  @(DynamoDBAttribute @field) @BeanProperty var x: Long,
  @(DynamoDBAttribute @field) @BeanProperty var y: Long
) {
  def this() = this(0, "", "", "", -1, 0, 0, 0, 0)
}
