package dev.faustin0.importer

import cats.data.OptionT
import cats.effect.{ IO, Ref }
import dev.faustin0.domain.{ BusStop, BusStopRepository }
import dev.faustin0.importer.domain._
import dev.faustin0.importer.infrastructure.S3BucketLoader
import dev.faustin0.repositories.DynamoBusStopRepository
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.util.Try

class Importer(busStopRepo: BusStopRepository[IO], datasetLoader: DataSetLoader[IO]) {
  implicit private val log: Logger[IO] = Slf4jLogger.getLogger[IO]
  private val concurrentOps            = 4

  def importFrom(dataset: DatasetFileLocation): IO[ImportOutcome] = {

    val value = for {
      counter  <- Ref.of[IO, Int](0)
      failures <- Stream
                    .eval(datasetLoader.load(dataset))
                    .flatMap(dataset => extractBusStopsFromDataSet(dataset))
                    .parEvalMap(concurrentOps) { busStop =>
                      OptionT(busStopRepo.findBusStopByCode(busStop.code))
                        .filter(existingBusStop => existingBusStop == busStop)
                        .value
                        .map(existingBusStop => busStop -> existingBusStop)
                    }
                    .collect { case (busStop, None) => busStop }
                    .evalTap(x => log.info(s"found busStop to update $x") *> counter.getAndUpdate(_ + 1))
                    .through(busStopRepo.batchInsert)
                    .evalTap(failure => log.error(failure.reason)("import operation failed"))
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
      .fromIterator[IO]((data.content \\ "NewDataSet" \\ "Table").iterator, 10) //todo chunk size ???
      .evalMapChunk(t => IO.fromEither(BusStop.fromXml(t)))
}

object Importer {

  def makeFromAWS(implicit L: Logger[IO]): Try[Importer] =
    for {
      busStopRepo  <- DynamoBusStopRepository.fromAWS()
      bucketReader <- S3BucketLoader.makeFromAws()
    } yield new Importer(busStopRepo, bucketReader)

}
