package dev.faustin0.repositories

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import dev.faustin0.domain.{ BusStop, BusStopRepository, FailureReason }
import fs2.{ Stream, _ }
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.http.SdkHttpClient
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import java.time.Duration
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success }

class DynamoBusStopRepository private (client: DynamoDbClient)(implicit L: Logger[IO]) extends BusStopRepository[IO] {

  override def insert(busStop: BusStop): IO[Unit] = {
    val request = PutItemRequest
      .builder()
      .tableName(BusStopTable.name)
      .item(BusStopTable.busStopToDynamoMapping(busStop))
      .build()

    L.debug(s"Inserting bus-stop $busStop") *>
      IO.blocking(client.putItem(request)).void
  }

  override def batchInsert: Pipe[IO, BusStop, FailureReason] = { busStops =>
    busStops
      .map(BusStopTable.busStopToDynamoMapping)
      .map(item => PutRequest.builder().item(item).build())
      .map(putReq => WriteRequest.builder().putRequest(putReq).build())
      .chunkN(BusStopTable.MAX_BATCH_SIZE, allowFewer = true)
      .map(chunk => java.util.Map.of(BusStopTable.name, chunk.toList.asJava))
      .map(writeReq =>
        BatchWriteItemRequest
          .builder()
          .requestItems(writeReq)
          .build()
      )
      .flatMap { batchReq =>
        Stream.attemptEval {
          L.debug(s"batch inserting bus-stop $batchReq") *>
            IO.blocking(client.batchWriteItem(batchReq))
        }.collect { case Left(error) =>
          FailureReason(error)
        }
      }

  }

  override def findBusStopByCode(code: Int): IO[Option[BusStop]] = {
    val values = BusStopTable.hashKeySearchParameters(code)

    val request = GetItemRequest
      .builder()
      .tableName(BusStopTable.name)
      .key(values)
      .build()

    for {
      _            <- L.debug(s"Getting busStop $code")
      result       <- IO.blocking(client.getItem(request))
      mappedBusStop = Option(result.item())
                        .filterNot(_.isEmpty)
                        .traverse(item => BusStopTable.dynamoItemToBusStop(item))
      busStop      <- mappedBusStop match {
                        case Failure(ex)    =>
                          IO.raiseError(new IllegalStateException(s"Failure getting busStop with code $code", ex))
                        case Success(value) =>
                          IO.pure(value)
                      }
    } yield busStop
  }

  override def count: IO[Long] = {
    val tableDesc = DescribeTableRequest
      .builder()
      .tableName(BusStopTable.name)
      .build()

    IO.blocking(client.describeTable(tableDesc))
      .map(resp => resp.table())
      .map(table => table.itemCount())
  }

  override def findBusStopByName(name: String): IO[List[BusStop]] = {
    val attributes = Map("#nameKey" -> BusStopTable.Attrs.busStopName)
    val values     = Map(":nameValue" -> AttributeValue.builder().s(name.toUpperCase).build())

    val queryRequest = QueryRequest
      .builder()
      .tableName(BusStopTable.name)
      .indexName(BusStopTable.searchIndexName)
      .consistentRead(false)
      .expressionAttributeNames(attributes.asJava)
      .expressionAttributeValues(values.asJava)
      .keyConditionExpression("#nameKey = :nameValue")
      .build()

    for {
      _       <- L.debug(s"Searching busStops $name")
      result  <- IO.blocking(client.query(queryRequest))
      busStop <- Option(result.items()).toList
                   .flatMap(_.asScala)
                   .traverse(item => BusStopTable.dynamoItemToBusStop(item))
                   .liftTo[IO]
    } yield busStop
  }

}

object DynamoBusStopRepository {

  def apply(awsClient: DynamoDbClient, l: Logger[IO]): DynamoBusStopRepository =
    new DynamoBusStopRepository(awsClient)(l)

  def makeResource(logger: Logger[IO]): Resource[IO, DynamoBusStopRepository] =
    Resource
      .fromAutoCloseable(
        IO.blocking(
          UrlConnectionHttpClient
            .builder()
            .connectionTimeout(Duration.ofSeconds(1))
            .build()
        )
      )
      .flatMap(c => Resource.fromAutoCloseable(clientFromEnv(c)))
      .map(c => DynamoBusStopRepository(c, logger))

  private def clientFromEnv(httpClient: SdkHttpClient): IO[DynamoDbClient] =
    IO.blocking {
      DynamoDbClient
        .builder()
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .region(Region.EU_CENTRAL_1)
        .httpClient(httpClient)
        .build()
    }

}
