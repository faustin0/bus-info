package dev.faustin0.importer.domain

trait DataSetLoader[F[_]] {
  def load(datasetFile: DatasetFileLocation): F[Dataset]
}
