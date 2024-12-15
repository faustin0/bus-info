package dev.faustin0.importer.infrastructure

import cats.effect.{ IO, Resource }
import dev.faustin0.importer.domain.{ Dataset, DataSetLoader, DatasetFileLocation }
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ GetObjectRequest, GetObjectResponse }

import scala.xml.XML

class S3BucketLoader(s3Client: S3Client) extends DataSetLoader[IO] {

  override def load(datasetFile: DatasetFileLocation): IO[Dataset] =
    readBucketObjectFromLocation(datasetFile)
      .use(responseStream => IO(XML.load(responseStream)))
      .map(xml => Dataset("TODO", xml))

  private def readBucketObjectFromLocation(
    datasetFile: DatasetFileLocation
  ): Resource[IO, ResponseInputStream[GetObjectResponse]] = {
    val getObjectRequest: GetObjectRequest = GetObjectRequest
      .builder()
      .bucket(datasetFile.bucketName)
      .key(datasetFile.fileName)
      .build()

    Resource
      .fromAutoCloseable(IO.blocking(s3Client.getObject(getObjectRequest)))
  }

}

object S3BucketLoader {

  def makeFromAws(): IO[S3BucketLoader] =
    IO(S3Client.builder().build())
      .map(new S3BucketLoader(_))

}
