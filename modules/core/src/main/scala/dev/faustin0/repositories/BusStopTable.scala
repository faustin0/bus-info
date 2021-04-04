package dev.faustin0.repositories

import dev.faustin0.domain.{ BusStop, Position }
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.util
import scala.jdk.CollectionConverters._
import scala.util.Try

object BusStopTable {

  val MAX_BATCH_SIZE  = 25
  val name            = "bus_stops"
  val indexHashKey    = "code"
  val searchIndexName = "name-index"

  case object Attrs {
    val busStopName = "name"
    val location    = "location"
    val comune      = "comune"
    val areaCode    = "areaCode"
    val lat         = "lat"
    val lng         = "lng"
    val x           = "x"
    val y           = "y"
  }

  def dynamoItemToBusStop(item: java.util.Map[String, AttributeValue]): Try[BusStop] =
    Try {
      BusStop(
        code = item.get(BusStopTable.indexHashKey).n().toInt,
        name = item.get(BusStopTable.Attrs.busStopName).s(),
        location = item.get(BusStopTable.Attrs.location).s(),
        comune = item.get(BusStopTable.Attrs.comune).s(),
        areaCode = item.get(BusStopTable.Attrs.areaCode).n().toInt,
        position = Position(
          x = item.get(BusStopTable.Attrs.x).n().toLong,
          y = item.get(BusStopTable.Attrs.y).n().toLong,
          lat = item.get(BusStopTable.Attrs.lat).n().toDouble,
          long = item.get(BusStopTable.Attrs.lng).n().toDouble
        )
      )
    }

  def busStopToDynamoMapping(busStop: BusStop): util.Map[String, AttributeValue] =
    Map(
      BusStopTable.indexHashKey      -> attribute(_.n(String.valueOf(busStop.code))),
      BusStopTable.Attrs.busStopName -> attribute(_.s(busStop.name)),
      BusStopTable.Attrs.location    -> attribute(_.s(busStop.location)),
      BusStopTable.Attrs.comune      -> attribute(_.s(busStop.comune)),
      BusStopTable.Attrs.areaCode    -> attribute(_.n(String.valueOf(busStop.areaCode))),
      BusStopTable.Attrs.lat         -> attribute(_.n(String.valueOf(busStop.position.lat))),
      BusStopTable.Attrs.lng         -> attribute(_.n(String.valueOf(busStop.position.long))),
      BusStopTable.Attrs.x           -> attribute(_.n(String.valueOf(busStop.position.x))),
      BusStopTable.Attrs.y           -> attribute(_.n(String.valueOf(busStop.position.y)))
    ).asJava

  def hashKeySearchParameters(code: Int): util.Map[String, AttributeValue] =
    Map(BusStopTable.indexHashKey -> AttributeValue.builder().n(String.valueOf(code)).build()).asJava

  private def attribute(b: AttributeValue.Builder => AttributeValue.Builder): AttributeValue = {
    val builder = AttributeValue.builder()
    b(builder)
    builder.build()
  }

}
