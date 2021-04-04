package dev.faustin0.repositories

import _root_.io.chrisdavenport.log4cats._
import _root_.io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.effect.{ ContextShift, IO, Resource }
import cats.implicits._
import dev.faustin0.domain.{ BusStop, BusStopRepository, FailureReason }
import dev.faustin0.repositories.DynamoBusStopRepository.JavaFutureOps
import fs2.{ Stream, _ }
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import java.net.URI
import java.util.concurrent.{ CancellationException, CompletableFuture }
import scala.jdk.CollectionConverters._
import scala.util.Try

class DynamoBusStopRepository private (private val client: DynamoDbAsyncClient)(implicit
  cs: ContextShift[IO]
) extends BusStopRepository[IO] {

  implicit private val log: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def insert(busStop: BusStop): IO[Unit] = {
    val request = PutItemRequest
      .builder()
      .tableName(BusStopTable.name)
      .item(BusStopTable.busStopToDynamoMapping(busStop))
      .build()

    log.debug(s"Inserting bus-stop $busStop") *>
      IO(client.putItem(request)).fromCompletable.void
  }

  override def batchInsert: Pipe[IO, BusStop, FailureReason] = { busStops =>
    busStops
      .map(busStop => BusStopTable.busStopToDynamoMapping(busStop))
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
          log.debug(s"batch inserting bus-stop $batchReq") *>
            IO(client.batchWriteItem(batchReq)).fromCompletable
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
      _            <- log.debug(s"Getting busStop $code")
      result       <- IO(client.getItem(request)).fromCompletable
      mappedBusStop = Option(result.item()).traverse(item => BusStopTable.dynamoItemToBusStop(item))
      busStop      <- IO.fromTry(mappedBusStop)
    } yield busStop
  }

  override def count: IO[Long] =
    IO(client.describeTable((b: DescribeTableRequest.Builder) => b.tableName(BusStopTable.name))).fromCompletable
      .map(resp => resp.table())
      .map(table => table.itemCount())

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
      _             <- log.debug(s"Searching busStops $name")
      result        <- IO(client.query(queryRequest)).fromCompletable
      mappedBusStops = Option(result.items()).toList
                         .flatMap(_.asScala)
                         .traverse(item => BusStopTable.dynamoItemToBusStop(item))
      busStop       <- IO.fromTry(mappedBusStops)
    } yield busStop
  }

}

object DynamoBusStopRepository {

  def apply(awsClient: DynamoDbAsyncClient)(implicit cs: ContextShift[IO]): DynamoBusStopRepository =
    new DynamoBusStopRepository(
      awsClient
    )

  def makeResource(implicit cs: ContextShift[IO]): Resource[IO, DynamoBusStopRepository] = {
    val client = IO.fromTry(awsDefaultClient.orElse(clientFromEnv))

    Resource
      .fromAutoCloseable(client)
      .map(DynamoBusStopRepository(_))
  }

  def fromAWS()(implicit cs: ContextShift[IO]): Try[DynamoBusStopRepository] =
    awsDefaultClient.map(DynamoBusStopRepository(_))

  private def awsDefaultClient: Try[DynamoDbAsyncClient] =
    Try {
      DynamoDbAsyncClient
        .builder()
        .build()
    }

  private def clientFromEnv: Try[DynamoDbAsyncClient] =
    Try {
      DynamoDbAsyncClient
        .builder()
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .region(Region.EU_CENTRAL_1)
        .endpointOverride(
          URI.create("http://localhost:4567") //TODO parametrize endpoint
        )
        .build()
    }

  implicit class JavaFutureOps[T](val unevaluatedCF: IO[CompletableFuture[T]]) extends AnyVal {

    def fromCompletable(implicit cs: ContextShift[IO]): IO[T] = {
      val computation: IO[T] = unevaluatedCF.flatMap { cf =>
        IO.cancelable { callback =>
          cf.handle((res: T, err: Throwable) =>
            err match {
              case null                     => callback(Right(res))
              case _: CancellationException => ()
              case ex                       => callback(Left(ex))
            }
          )
          IO.delay(cf.cancel(true))
        }
      }
      computation.guarantee(cs.shift)
    }
  }
}
