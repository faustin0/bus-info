package repositories

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import models.BusStop

class BusStopRepository(private val awsClient: AmazonDynamoDB) {

  private val mapper = new DynamoDBMapper(awsClient)

  def insert(busStop: BusStop) = {

    mapper.save(BusStopEntity(busStop.code, busStop.name))
  }
}

object BusStopRepository {}