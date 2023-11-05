package dev.faustin0

import cats.effect.std.Dispatcher
import cats.effect.{ IO, Resource }
import com.dimafeng.testcontainers.{ GenericContainer, LocalStackV2Container }
import dev.faustin0.repositories.DynamoBusStopRepository.EmberAsyncHttpClient
import org.http4s.client.middleware
import org.http4s.ember.client.EmberClientBuilder
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.containers.wait.strategy.Wait
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.auth.credentials.{ AwsBasicCredentials, StaticCredentialsProvider }
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.s3.S3Client

import java.net.URI

trait Containers {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  lazy val localStack: LocalStackV2Container = LocalStackV2Container(services = List(Service.S3))

  lazy val dynamoContainer: GenericContainer = GenericContainer(
    dockerImage = "amazon/dynamodb-local",
    exposedPorts = Seq(8000),
    command = Seq("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory"),
    waitStrategy = Wait.forLogMessage(".*CorsParams:.*", 1)
  )

}

object Containers {

  def createS3Client(container: LocalStackV2Container): Resource[IO, S3Client] = {
    val credentials = StaticCredentialsProvider.create(container.staticCredentialsProvider.resolveCredentials())
    Resource.fromAutoCloseable(IO {
      S3Client.builder
        .endpointOverride(container.endpointOverride(LocalStackContainer.Service.S3))
        .credentialsProvider(credentials)
        .region(container.region)
        .build()
    })
  }

  def createDynamoClient(dynamoContainer: GenericContainer): Resource[IO, DynamoDbAsyncClient] = {
    lazy val dynamoDbEndpoint =
      s"http://${dynamoContainer.container.getHost}:${dynamoContainer.container.getFirstMappedPort}"

    for {
      emberClient  <- EmberClientBuilder.default[IO].build
      loggedCLient  = middleware.Logger(logHeaders = true, logBody = true)(emberClient) //todo move to EmberAsyncHttpClient
      dispatcher   <- Dispatcher.sequential[IO](await = true)
      dynamoClient <- Resource.fromAutoCloseable {
                        IO(
                          DynamoDbAsyncClient
                            .builder()
                            .region(Region.EU_CENTRAL_1)
                            .credentialsProvider(
                              StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy"))
                            )
                            .endpointOverride(URI.create(dynamoDbEndpoint))
                            .httpClient(new EmberAsyncHttpClient(loggedCLient, dispatcher))
                            .build()
                        )
                      }
    } yield dynamoClient

  }

}
