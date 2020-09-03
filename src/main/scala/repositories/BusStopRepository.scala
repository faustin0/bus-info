package repositories

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

  def findBusStopByName(name: String): Stream[IO, BusStop] = {
    val values     = Map(":nameValue" -> new AttributeValue().withS(name.toUpperCase))
    val attributes = Map("#nameKey" -> "name")

    val queryExpression = new DynamoDBQueryExpression[BusStopEntity]()
      .withIndexName("name-index")
      .withConsistentRead(false)
      .withExpressionAttributeNames(attributes.asJava)
      .withExpressionAttributeValues(values.asJava)
      .withKeyConditionExpression("#nameKey = :nameValue")

    Stream
      .evalSeq(IO(mapper.query(classOf[BusStopEntity], queryExpression).asScala.toList))
      .map(_.as[BusStop])
  }
}
