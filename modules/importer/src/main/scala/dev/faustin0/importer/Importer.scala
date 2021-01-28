package dev.faustin0.importer

import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import dev.faustin0.domain.BusStop
import dev.faustin0.importer.domain._
import dev.faustin0.importer.infrastructure.S3BucketLoader
import dev.faustin0.repositories.BusStopRepository
import fs2.Stream
import software.amazon.awssdk.services.s3.S3Client

class Importer(busStopRepo: BusStopRepository, datasetLoader: DataSetLoader[IO]) {

  def importFrom(dataset: DatasetFileLocation): IO[ImportOutcome] =
    for {
      busStopsDataset <- datasetLoader.load(dataset)
      busStops        <- extractBusStopsFromDataSet(busStopsDataset).compile.toList
      failed          <- busStopRepo.batchInsert(busStops).find(_.getUnprocessedItems.size() > 0).compile.toList
    } yield failed match {
      case List(errors) => Failure(busStopsDataset.name, busStops.length, errors)
      case Nil          => Success(busStopsDataset.name, busStops.length)
    }

  private def extractBusStopsFromDataSet(data: BusStopsDataset): Stream[IO, BusStop] =
    Stream
      .fromIterator[IO]((data.content \\ "NewDataSet" \\ "Table").iterator)
      .evalMapChunk(t => IO.fromEither(BusStop.fromXml(t)))
}

object Importer {

  def makeFromAWS(): Importer = {
    lazy val s3Client     = S3Client.builder().build()
    lazy val dynamoClient = AmazonDynamoDBClientBuilder.defaultClient()
    lazy val busStopRepo  = BusStopRepository(dynamoClient)
    lazy val bucketReader = new S3BucketLoader(s3Client)

    new Importer(busStopRepo, bucketReader)
  }
}
