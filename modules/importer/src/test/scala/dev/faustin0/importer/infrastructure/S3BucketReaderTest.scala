package dev.faustin0.importer.infrastructure

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

    val s3 = createS3Client(localStack)

    val fileSource = getClass.getClassLoader.getResource("bus-stop-dataset.xml")

    s3.createBucket((b: CreateBucketRequest.Builder) => b.bucket("bus-stops"))
    s3.putObject(
      (b: PutObjectRequest.Builder) => {
        b.bucket("bus-stops").key("bus-stop-dataset.xml")
        ()
      },
      Paths.get(fileSource.toURI)
    )
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
