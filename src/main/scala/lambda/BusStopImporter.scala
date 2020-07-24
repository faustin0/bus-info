package lambda

import cats.effect.IO
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.s3.model.{GetObjectRequest, S3Event => S3EventType}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import models.{BusStop, TransformError}

import scala.jdk.CollectionConverters._
import scala.xml.{NodeSeq, XML}

class BusStopImporter extends RequestHandler[S3Event, String] {

  private lazy val s3Client: AmazonS3 = AmazonS3ClientBuilder.defaultClient()

  override def handleRequest(s3Event: S3Event, context: Context): String = {
    val logger = context.getLogger

    val fileCreatedEvent = s3Event.getRecords.asScala
      .find(e => e.getEventName.equals(S3EventType.ObjectCreated.toString))

    val res = fileCreatedEvent
      .fold("No file to process")(e => {
        val bucket = e.getS3.getBucket.getName
        val srcKey = e.getS3.getObject.getUrlDecodedKey
        val objReq = new GetObjectRequest(bucket, srcKey)

        val computation = for {
          s3Object <- IO(s3Client.getObject(objReq))
          content <- IO(s3Object.getObjectContent)
          xml <- IO(XML.load(content))
          busStops <- IO.fromEither(BusStopImporter.parseXmlDataset(xml))
        } yield (busStops, s"processed ${busStops.length} entry")

        computation.unsafeRunSync()._2
      })
    logger.log(res)
    res
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
}
