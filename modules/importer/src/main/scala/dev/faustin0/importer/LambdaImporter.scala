package dev.faustin0.importer

import cats.effect.unsafe.IORuntime
import cats.effect.{ ExitCode, IO }
import cats.implicits._
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import dev.faustin0.importer.domain.DatasetFileLocation
import dev.faustin0.importer.infrastructure.S3BucketLoader
import dev.faustin0.repositories.DynamoBusStopRepository
import fs2.Stream
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.services.dynamodb.{ DynamoDbAsyncClient, DynamoDbClient }

import scala.jdk.CollectionConverters.CollectionHasAsScala

class LambdaImporter() extends RequestHandler[S3Event, ExitCode] {

  implicit private lazy val runtime: IORuntime = IORuntime.global

  override def handleRequest(s3Event: S3Event, context: Context): ExitCode = {
    implicit val logger = Slf4jLogger.getLogger[IO].addContext(Map("RequestId" -> context.getAwsRequestId))

    val computation = for {
      busStopRepo  <- IO(DynamoDbClient.create()).map(DynamoBusStopRepository.apply(_, logger)) // todo client
      bucketReader <- S3BucketLoader.makeFromAws()
      importer      = new Importer(busStopRepo, bucketReader)
      _            <- Stream
                        .fromIterator[IO](s3Event.getRecords.asScala.iterator, 10) // TODO chunk size
                        .evalTap(s3Event => logger.info(s"S3 event: ${s3Event.toString}"))
                        .find(e => e.getEventName.contains("ObjectCreated:"))
                        .map(s3Record =>
                          DatasetFileLocation(
                            bucketName = s3Record.getS3.getBucket.getName,
                            fileName = s3Record.getS3.getObject.getUrlDecodedKey
                          )
                        )
                        .evalMap(dataset => importer.importFrom(dataset))
                        .evalTap(outcome => logger.info(outcome.show))
                        .compile
                        .drain
    } yield ExitCode.Success

    computation.unsafeRunSync()
  }

  private def awsDefaultClient: IO[DynamoDbAsyncClient] =
    IO(DynamoDbAsyncClient.create())

}
