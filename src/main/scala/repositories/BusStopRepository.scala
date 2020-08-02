package repositories

import cats.data.OptionT
import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import models.{BusStop, Position}

class BusStopRepository(private val awsClient: AmazonDynamoDB) {

  private val mapper = new DynamoDBMapper(awsClient)

  def insert(busStop: BusStop): IO[Unit] = {
    IO {
      mapper.save(
        BusStopEntity(
          code = busStop.code,
          name = busStop.name,
          location = busStop.location,
          comune = busStop.comune,
          areaCode = busStop.areaCode,
          lat = busStop.position.lat,
          lng = busStop.position.long,
          x = busStop.position.x,
          y = busStop.position.y
        )
      )
    }
  }

  def findBusStopByCode(code: Long): OptionT[IO, BusStop] = {
    OptionT
      .fromOption[IO](Option(mapper.load(classOf[BusStopEntity], code)))
      .map { e =>
        BusStop(
          code = e.code,
          name = e.name,
          location = e.location,
          comune = e.comune,
          areaCode = e.areaCode,
          position = Position(e.x, e.y, e.lat, e.lng)
        )
      }
  }
}
