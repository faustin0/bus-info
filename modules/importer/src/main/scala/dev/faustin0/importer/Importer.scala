package dev.faustin0.importer

import cats.effect.concurrent.Ref
import cats.effect.{ ContextShift, IO }
import dev.faustin0.domain.{ BusStop, BusStopRepository }
import dev.faustin0.importer.domain._
import dev.faustin0.importer.infrastructure.S3BucketLoader
import dev.faustin0.repositories.DynamoBusStopRepository
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.util.Try

class Importer(busStopRepo: BusStopRepository[IO], datasetLoader: DataSetLoader[IO]) {
  implicit private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def importFrom(dataset: DatasetFileLocation): IO[ImportOutcome] = {

    val value = for {
      counter  <- Ref.of[IO, Int](0)
      failures <- Stream
                    .eval(datasetLoader.load(dataset))
                    .flatMap(dataset => extractBusStopsFromDataSet(dataset))
                    .evalTap(_ => counter.getAndUpdate(_ + 1))
                    .through(busStopRepo.batchInsert)
                    .evalTap(failure => logger.error(failure.reason)("import operation failed"))
                    .compile
                    .fold(0)((acc, _) => acc + 1)
      count    <- counter.get
    } yield (failures, count)

    value.map {
      case (0, processed)     => Success(dataset.fileName, processed)
      case (fails, processed) => Failure(dataset.fileName, processed, fails)
    }
  }

  private def extractBusStopsFromDataSet(data: BusStopsDataset): Stream[IO, BusStop] =
    Stream
      .fromIterator[IO]((data.content \\ "NewDataSet" \\ "Table").iterator)
      .evalMapChunk(t => IO.fromEither(BusStop.fromXml(t)))
}

object Importer {

  def makeFromAWS(implicit cs: ContextShift[IO]): Try[Importer] =
    for {
      busStopRepo  <- DynamoBusStopRepository.fromAWS()
      bucketReader <- S3BucketLoader.makeFromAws()
    } yield new Importer(busStopRepo, bucketReader)

}
