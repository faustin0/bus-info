package repositories

import cats.data.OptionT
import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch
import com.amazonaws.services.dynamodbv2.datamodeling.{DynamoDBMapper, DynamoDBScanExpression}
import com.amazonaws.services.dynamodbv2.model.Select
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
          s.compile.toList
            .map(_.asJava)
            .map(l => mapper.batchSave(l).asScala.toList)
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
}
