package dev.faustin0.importer

import cats.effect.{ ExitCode, IO }
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import dev.faustin0.importer.domain.DatasetFileLocation
import fs2.Stream

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

class LambdaImporter() extends RequestHandler[S3Event, ExitCode] {
  implicit lazy val cs = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))

  private lazy val importer = Importer.makeFromAWS.get

  override def handleRequest(s3Event: S3Event, context: Context): ExitCode = {
    val logger = context.getLogger

    Stream
      .fromIterator[IO](s3Event.getRecords.asScala.iterator)
      .evalTap(s3Event => IO(logger.log(s"S3 event: ${s3Event.getEventName}")))
      .find(e => e.getEventName.contains("ObjectCreated:"))
      .map(s3Record =>
        DatasetFileLocation(
          bucketName = s3Record.getS3.getBucket.getName,
          fileName = s3Record.getS3.getObject.getUrlDecodedKey
        )
      )
      .evalMap(dataset => importer.importFrom(dataset))
      .evalTap(outCome => IO(logger.log(outCome.toString)))
      .compile
      .drain
      .as(ExitCode.Success)
      .unsafeRunSync()
  }
}
