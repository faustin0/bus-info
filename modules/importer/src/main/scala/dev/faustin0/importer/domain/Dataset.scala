package dev.faustin0.importer.domain

import cats.Show

import scala.xml.Elem

case class DatasetFileLocation(
  bucketName: String,
  fileName: String
)

case class BusStopsDataset(
  name: String,
  content: Elem
)

sealed trait ImportOutcome extends Product with Serializable

final case class Success(
  processedFileName: String,
  processedItems: Int
) extends ImportOutcome

final case class Failure(
  processedFile: String,
  processedItems: Int,
  failures: Int
) extends ImportOutcome

object ImportOutcome {

  implicit val showForImportOutcome: Show[ImportOutcome] = {
    case Success(processedFileName, processedItems)       =>
      s"""
         |successfully processed file: '$processedFileName'
         |extracted items count: '$processedItems'
         |""".stripMargin
    case Failure(processedFile, processedItems, failures) =>
      s"""
         |Failed to process file: '$processedFile'
         |extracted items count: '$processedItems'
         |failed inserts count: '$failures'
         |""".stripMargin

  }

}
