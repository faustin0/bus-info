package dev.faustin0.importer.domain

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch

import java.io.{ PrintWriter, StringWriter }
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
) extends ImportOutcome {

  override def toString: String = //todo rename, do not use to string
    s"""
       |successfully processed file: '$processedFileName'
       |extracted items count: '$processedItems'
       |""".stripMargin
}

final case class Failure(
  processedFile: String,
  processedItems: Int,
  failure: FailedBatch //todo remove aws deps
) extends ImportOutcome {

  private def getStackTrace(t: Throwable): String = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    t.printStackTrace(pw)
    sw.toString
  }

  private def formatException(t: Throwable): String = s"$t ${getStackTrace(t)}"

  override def toString: String =
    s"""
       |processed file: '$processedFile'
       |extracted items count: '$processedItems'
       |failed inserts count: '${failure.getUnprocessedItems.size()}'
       |${formatException(failure.getException)}
       |""".stripMargin
}
