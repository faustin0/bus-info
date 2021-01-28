package dev.faustin0.importer

import cats.effect.IO
import cats.implicits.toFlatMapOps
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import dev.faustin0.importer.domain.DatasetFileLocation

import scala.jdk.CollectionConverters.CollectionHasAsScala

class LambdaImporter() extends RequestHandler[S3Event, Unit] {

  private lazy val importer = Importer.makeFromAWS()

  override def handleRequest(s3Event: S3Event, context: Context): Unit = {
    val logger = context.getLogger

    s3Event.getRecords.forEach(it => logger.log(s"S3 event: ${it.getEventName}"))

    getCreatedObject(s3Event).foreach { dataset =>
      importer
        .importFrom(dataset)
        .flatTap(outCome => IO(logger.log(outCome.toString)))
        .void
        .unsafeRunSync()
    }
  }

  private def getCreatedObject(s3Event: S3Event): Option[DatasetFileLocation] =
    s3Event.getRecords.asScala
      .find(e => e.getEventName.contains("ObjectCreated:"))
      .map { s3Record =>
        DatasetFileLocation(
          bucketName = s3Record.getS3.getBucket.getName,
          fileName = s3Record.getS3.getObject.getUrlDecodedKey
        )
      }

}
