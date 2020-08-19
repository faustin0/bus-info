package repositories

import java.util.{HashMap => JavaMap}

import cats.data.OptionT
import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch
import com.amazonaws.services.dynamodbv2.datamodeling.{
  DynamoDBMapper,
  DynamoDBQueryExpression,
  DynamoDBScanExpression
}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, Select}
import fs2._
import models.BusStop

import scala.jdk.CollectionConverters._

class BusStopRepository(private val awsClient: AmazonDynamoDB) {

  private val mapper = new DynamoDBMapper(awsClient)

  def insert(busStop: BusStop): IO[Unit] = {
    IO {
      mapper.save(BusStopEntity.fromBusStop(busStop))
    }
  }

  def batchInsert(busStops: List[BusStop]): Stream[IO, FailedBatch] = {
    Stream
      .emits(busStops)
      .covary[IO]
      .map(BusStopEntity.fromBusStop)
      .through(s => {
        Stream.evalSeq(
          for {
            stops <- s.compile.toList
            stopsJava = stops.asJava
            failures <- IO(mapper.batchSave(stopsJava))
          } yield failures.asScala.toList
        )
      })
  }

  def findBusStopByCode(code: Long): OptionT[IO, BusStop] = {
    OptionT
      .fromOption[IO](Option(mapper.load(classOf[BusStopEntity], code)))
      .map { _.as[BusStop] }
  }

  def count(): IO[Int] =
    IO {
      val query = new DynamoDBScanExpression
      query.setSelect(Select.COUNT)
      mapper.count(classOf[BusStopEntity], query)
    }

  def findBusStopByName(name: String): OptionT[IO, BusStop] = {
    val eav = new JavaMap[String, AttributeValue]()
    eav.put(":v1", new AttributeValue().withS(name.toUpperCase))

    val expressionAttributesNames = new JavaMap[String, String]()
    expressionAttributesNames.put("#name", "name")

    val queryExpression = new DynamoDBQueryExpression[BusStopEntity]()
      .withIndexName("name-index")
      .withConsistentRead(false)
      .withExpressionAttributeNames(expressionAttributesNames)
      .withExpressionAttributeValues(eav)
      .withKeyConditionExpression("#name = :v1")

    OptionT
      .liftF(IO(mapper.query(classOf[BusStopEntity], queryExpression)))
      .map(_.asScala)
      .subflatMap(_.headOption)
      .map(_.as[BusStop])
  }
}
