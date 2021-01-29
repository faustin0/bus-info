package dev.faustin0.importer.infrastructure

import cats.effect.IO
import cats.effect.testing.scalatest.{ AssertingSyntax, AsyncIOSpec }
import com.dimafeng.testcontainers.{ Container, ForAllTestContainer, LocalStackV2Container }
import dev.faustin0.importer.domain.DatasetFileLocation
import org.scalatest.freespec.AsyncFreeSpec
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ CreateBucketRequest, PutObjectRequest }

import java.nio.file.Paths
import scala.io.Source
import scala.xml.Utility.trim
import scala.xml.{ Elem, XML }

class S3BucketReaderTest extends AsyncFreeSpec with AsyncIOSpec with ForAllTestContainer with AssertingSyntax {

  private lazy val localStack = LocalStackV2Container(services = List(Service.S3))

  override val container: Container = localStack

  override def afterStart(): Unit = {
    val bucketInit = for {
      s3Client       <- IO(createS3Client(localStack))
      fileSource     <- IO(getClass.getClassLoader.getResource("bus-stop-dataset.xml"))
      createBucketReq = CreateBucketRequest.builder().bucket("bus-stops").build()
      putObjReq       = PutObjectRequest.builder().bucket("bus-stops").key("bus-stop-dataset.xml").build()
      _              <- IO(s3Client.createBucket(createBucketReq))
      _              <- IO(s3Client.putObject(putObjReq, Paths.get(fileSource.toURI)))
    } yield ()

    bucketInit.unsafeRunSync()
  }

  "should load a file from a bucket" in {
    val s3: S3Client = createS3Client(localStack)

    val expected = XML.loadString(Source.fromResource("bus-stop-dataset.xml").mkString)

    new S3BucketLoader(s3)
      .load(DatasetFileLocation("bus-stops", "bus-stop-dataset.xml"))
      .asserting { dataset =>
        assert(xmlEqualsIgnoringWhiteSpaces(actual = dataset.content, expected))
      }
  }

  private def createS3Client(container: LocalStackV2Container) = {
    val credentials = StaticCredentialsProvider.create(container.staticCredentialsProvider.resolveCredentials())

    S3Client.builder
      .endpointOverride(container.endpointOverride(LocalStackContainer.Service.S3))
      .credentialsProvider(credentials)
      .region(container.region)
      .build()
  }

  private def xmlEqualsIgnoringWhiteSpaces(actual: Elem, expected: Elem) =
    trim(actual) === trim(expected)
}
