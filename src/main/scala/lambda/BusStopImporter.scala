package lambda

import java.io.{PrintWriter, StringWriter}

import cats.effect.{IO, Resource}
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.s3.model.{GetObjectRequest, S3Object}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import fs2.Stream
import models.{BusStop, TransformError}
import repositories.BusStopRepository

import scala.jdk.CollectionConverters._
import scala.xml.{Elem, NodeSeq, XML}

class BusStopImporter(
  s3Client: AmazonS3,
  dynamoClient: AmazonDynamoDB
) extends RequestHandler[S3Event, Unit] {
  def this() = //no-args constructor for lambda invocation
    this(
      s3Client = AmazonS3ClientBuilder.defaultClient(),
      dynamoClient = AmazonDynamoDBClientBuilder.defaultClient()
    )

  override def handleRequest(s3Event: S3Event, context: Context): Unit = {
    val logger = context.getLogger
    val repo   = new BusStopRepository(dynamoClient)
    s3Event.getRecords.forEach(it => logger.log(s"S3 event: ${it.getEventName}"))

    val outcome = s3Event.getRecords.asScala
      .find(e => e.getEventName.contains("ObjectCreated:")) // todo there must be a better way ...
      .map { e =>
        readBucketObjectFromEvent(e)
          .use(obj => {
            for {
              elem     <- parseS3Object(obj)
              busStops <- BusStopImporter.streamParseXmlDataset(elem).compile.toList
              failed <- repo.batchInsert(busStops).find(_.getUnprocessedItems.size() > 0).compile.toList
            } yield ImporterOutcome(obj.getKey, busStops.length, failed.headOption)
          })
      }
      .getOrElse(IO.pure(ImporterOutcome("No file to process", 0)))
      .unsafeRunSync()

    logger.log(outcome.toString)
  }

  private def readBucketObjectFromEvent(e: S3EventNotificationRecord): Resource[IO, S3Object] = {
    val bucket = e.getS3.getBucket.getName
    val srcKey = e.getS3.getObject.getUrlDecodedKey
    val objReq = new GetObjectRequest(bucket, srcKey)

    Resource.fromAutoCloseable(IO(s3Client.getObject(objReq)))
  }

  private def parseS3Object(s3Object: S3Object): IO[Elem] = {
    for {
      content <- IO(s3Object.getObjectContent)
      xml     <- IO(XML.load(content))
    } yield xml
  }

}

object BusStopImporter {
  def parseXmlDataset(xml: NodeSeq): Either[TransformError, Seq[BusStop]] = {
    (xml \\ "NewDataSet" \\ "Table")
      .map(t => BusStop.fromXml(t))
      .partitionMap(identity) match {
      case (Nil, busStops) => Right(busStops)
      case (errors, _)     => Left(errors.head)
    }
  }

  def streamParseXmlDataset(xml: NodeSeq): Stream[IO, BusStop] = {
    Stream
      .fromIterator[IO]((xml \\ "NewDataSet" \\ "Table").iterator)
      .evalMapChunk(t => IO.fromEither(BusStop.fromXml(t)))
  }
}
case class ImporterOutcome(
  processedFile: String,
  processedItems: Int,
  failure: Option[FailedBatch] = None
) {

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
       |failed inserts count: '${failure.map(_.getUnprocessedItems.size()).getOrElse(0)}'
       |${failure.map(f => formatException(f.getException)).getOrElse("")}
       |""".stripMargin
}
