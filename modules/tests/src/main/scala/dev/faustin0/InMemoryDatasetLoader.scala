package dev.faustin0

import cats.effect.IO
import dev.faustin0.importer.domain.{ BusStopsDataset, DataSetLoader, DatasetFileLocation }

class InMemoryDatasetLoader(bucketName: String) extends DataSetLoader[IO] {

  override def load(datasetFile: DatasetFileLocation): IO[BusStopsDataset] =
    getTestDataset.flatMap { testDataset =>
      if (datasetFile.bucketName == bucketName && datasetFile.fileName == testDataset.name)
        IO.pure(testDataset)
      else
        IO.raiseError(
          new IllegalArgumentException(
            s"Mismatch between requested $datasetFile and provided $bucketName  ${testDataset.name}"
          )
        )
    }

  def getBusStopsEntriesNumber: IO[Int] =
    getTestDataset
      .map(_.content)
      .map(e => (e \\ "Table").size)

  private def getTestDataset: IO[BusStopsDataset] =
    IO(getClass.getClassLoader.getResource("bus-stop-dataset.xml"))
      .flatMap(f => IO(xml.XML.load(f)))
      .map(xml => BusStopsDataset("bus-stop-dataset.xml", xml))

}
