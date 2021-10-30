package dev.faustin0

import cats.effect.IO
import dev.faustin0.importer.domain.{ BusStopsDataset, DataSetLoader, DatasetFileLocation }

class InMemoryDatasetLoader(bucketName: String, private val stubbedDataset: BusStopsDataset) extends DataSetLoader[IO] {

  override def load(datasetFile: DatasetFileLocation): IO[BusStopsDataset] =
    if (datasetFile.bucketName == bucketName && datasetFile.fileName == stubbedDataset.name)
      IO.pure(stubbedDataset)
    else
      IO.raiseError(
        new IllegalArgumentException(
          s"Mismatch between requested $datasetFile and provided $bucketName  ${stubbedDataset.name}"
        )
      )

}
