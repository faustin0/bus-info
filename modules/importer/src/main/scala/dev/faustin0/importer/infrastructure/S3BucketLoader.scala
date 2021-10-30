package dev.faustin0.importer.infrastructure

import cats.effect.{ IO, Resource }
import dev.faustin0.importer.domain.{ BusStopsDataset, DataSetLoader, DatasetFileLocation }
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ GetObjectRequest, GetObjectResponse }

import scala.util.Try
import scala.xml.XML

class S3BucketLoader(private val s3Client: S3Client) extends DataSetLoader[IO] {

  override def load(datasetFile: DatasetFileLocation): IO[BusStopsDataset] =
    readBucketObjectFromLocation(datasetFile)
      .use(responseStream => IO(XML.load(responseStream)))
      .map(xml => BusStopsDataset("TODO", xml))

  private def readBucketObjectFromLocation(
    datasetFile: DatasetFileLocation
  ): Resource[IO, ResponseInputStream[GetObjectResponse]] = {
    val getObjectRequest: GetObjectRequest = GetObjectRequest
      .builder()
      .bucket(datasetFile.bucketName)
      .key(datasetFile.fileName)
      .build()

    Resource
      .fromAutoCloseable(IO(s3Client.getObject(getObjectRequest)))
  }

}

object S3BucketLoader {

  def makeFromAws(): Try[S3BucketLoader] =
    Try(S3Client.builder().build())
      .map(new S3BucketLoader(_))

}
