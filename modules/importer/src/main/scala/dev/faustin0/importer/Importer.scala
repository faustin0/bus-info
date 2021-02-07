package dev.faustin0.importer

import cats.effect.{ ContextShift, IO }
import dev.faustin0.domain.BusStop
import dev.faustin0.importer.domain._
import dev.faustin0.importer.infrastructure.S3BucketLoader
import dev.faustin0.repositories.BusStopRepository
import fs2.Stream

import scala.util.Try

class Importer(busStopRepo: BusStopRepository, datasetLoader: DataSetLoader[IO]) {

  def importFrom(dataset: DatasetFileLocation): IO[ImportOutcome] =
    Stream
      .eval(datasetLoader.load(dataset))
      .flatMap(dataset => extractBusStopsFromDataSet(dataset))
      .through(busStopRepo.batchInsert)
      //      .filter(resp => resp.hasUnprocessedItems) todo
      //      .map(resp => resp.unprocessedItems())
      .map(_.unprocessedItems().size)
      .reduce((r1, r2) => r1 + r2)
      .compile
      .toList
      .map {
        case List(errorsCount) => Failure(dataset.fileName, errorsCount)
        case _                 => Success(dataset.fileName, 0)
      }

  private def extractBusStopsFromDataSet(data: BusStopsDataset): Stream[IO, BusStop] =
    Stream
      .fromIterator[IO]((data.content \\ "NewDataSet" \\ "Table").iterator)
      .evalMapChunk(t => IO.fromEither(BusStop.fromXml(t)))
}

object Importer {

  def makeFromAWS(implicit cs: ContextShift[IO]): Try[Importer] =
    for {
      busStopRepo  <- BusStopRepository.fromAWS()
      bucketReader <- S3BucketLoader.makeFromAws()
    } yield new Importer(busStopRepo, bucketReader)

}
