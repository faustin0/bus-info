package dev.faustin0.importer

import cats.data.OptionT
import cats.effect.{ Async, Ref }
import cats.syntax.all._
import dev.faustin0.domain.{ BusStop, BusStopRepository }
import dev.faustin0.importer.domain._
import fs2.Stream
import org.typelevel.log4cats.Logger

class Importer[F[_]: Async: Logger](busStopRepo: BusStopRepository[F], datasetLoader: DataSetLoader[F]) {
  private val concurrentOps = 4

  def importFrom(dataset: DatasetFileLocation): F[ImportOutcome] = {

    val value = for {
      counter  <- Ref[F].of(0)
      failures <- Stream
                    .eval(datasetLoader.load(dataset))
                    .flatMap(dataset => extractBusStopsFromDataSet(dataset))
                    .parEvalMap(concurrentOps) { busStop =>
                      OptionT(busStopRepo.findBusStopByCode(busStop.code))
                        .filter(_ == busStop)
                        .value
                        .map(existingBusStop => busStop -> existingBusStop)
                    }
                    .collect { case (busStop, None) => busStop }
                    .evalTap(x => Logger[F].info(s"found busStop to update $x") *> counter.update(_ + 1))
                    .through(busStopRepo.batchInsert)
                    .evalTap(failure => Logger[F].error(failure.reason)("import operation failed"))
                    .compile
                    .fold(0)((acc, _) => acc + 1)
      count    <- counter.get
    } yield (failures, count)

    value.map {
      case (0, processed)     => Success(dataset.fileName, processed)
      case (fails, processed) => Failure(dataset.fileName, processed, fails)
    }
  }

  private def extractBusStopsFromDataSet(data: BusStopsDataset): Stream[F, BusStop] =
    Stream
      .fromIterator[F]((data.content \\ "NewDataSet" \\ "Table").iterator, 10) // todo chunk size ???
      .evalMapChunk(t => BusStop.fromXml(t).liftTo[F])

}
