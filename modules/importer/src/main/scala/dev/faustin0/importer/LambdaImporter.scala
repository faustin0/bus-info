package dev.faustin0.importer

import cats.effect.unsafe.IORuntime
import cats.effect.{ ExitCode, IO }
import cats.implicits._
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import dev.faustin0.importer.domain.DatasetFileLocation
import fs2.Stream
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.jdk.CollectionConverters.CollectionHasAsScala

class LambdaImporter() extends RequestHandler[S3Event, ExitCode] {

  implicit private lazy val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  implicit private lazy val runtime: IORuntime                    = IORuntime.global
  private lazy val importer: Importer                             = Importer.makeFromAWS.get

  override def handleRequest(s3Event: S3Event, context: Context): ExitCode = {
    val logger = context.getLogger

    Stream
      .fromIterator[IO](s3Event.getRecords.asScala.iterator, 10) //TODO chunk size
      .evalTap(s3Event => IO(logger.log(s"S3 event: ${s3Event.getEventName}")))
      .find(e => e.getEventName.contains("ObjectCreated:"))
      .map(s3Record =>
        DatasetFileLocation(
          bucketName = s3Record.getS3.getBucket.getName,
          fileName = s3Record.getS3.getObject.getUrlDecodedKey
        )
      )
      .evalMap(dataset => importer.importFrom(dataset))
      .evalTap(outcome => IO(logger.log(outcome.show)))
      .compile
      .drain
      .as(ExitCode.Success)
      .unsafeRunSync()
  }

}
