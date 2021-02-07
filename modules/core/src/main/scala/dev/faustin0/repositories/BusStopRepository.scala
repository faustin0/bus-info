package dev.faustin0.repositories

import cats.effect.{ ContextShift, IO, Resource }
import cats.implicits._
import dev.faustin0.domain.{ BusStop, Position }
import dev.faustin0.repositories.BusStopRepository.{ JavaFutureOps, Table }
import fs2._
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import java.net.URI
import java.util
import java.util.concurrent.{ CancellationException, CompletableFuture }
import scala.jdk.CollectionConverters._
import scala.util.Try

class BusStopRepository private (private val client: DynamoDbAsyncClient)(implicit cs: ContextShift[IO]) {

  def insert(busStop: BusStop): IO[Unit] = {
    val request = PutItemRequest
      .builder()
      .tableName(Table.name)
      .item(
        Map(
          Table.indexHashKey      -> attribute(_.n(String.valueOf(busStop.code))),
          Table.Attrs.busStopName -> attribute(_.s(busStop.name)),
          Table.Attrs.location    -> attribute(_.s(busStop.location)),
          Table.Attrs.comune      -> attribute(_.s(busStop.comune)),
          Table.Attrs.areaCode    -> attribute(_.n(String.valueOf(busStop.areaCode))),
          Table.Attrs.lat         -> attribute(_.n(String.valueOf(busStop.position.lat))),
          Table.Attrs.lng         -> attribute(_.n(String.valueOf(busStop.position.long))),
          Table.Attrs.x           -> attribute(_.n(String.valueOf(busStop.position.x))),
          Table.Attrs.y           -> attribute(_.n(String.valueOf(busStop.position.y)))
        ).asJava
      )
      .build()

//    log.info(s"Inserting bus-stop $busStop") *>
    IO(client.putItem(request)).fromCompletable.void
  }

  def batchInsert: Pipe[IO, BusStop, BatchWriteItemResponse] = { busStops =>
    busStops.map { busStop =>
      Map(
        Table.indexHashKey      -> attribute(_.n(String.valueOf(busStop.code))),
        Table.Attrs.busStopName -> attribute(_.s(busStop.name)),
        Table.Attrs.location    -> attribute(_.s(busStop.location)),
        Table.Attrs.comune      -> attribute(_.s(busStop.comune)),
        Table.Attrs.areaCode    -> attribute(_.n(String.valueOf(busStop.areaCode))),
        Table.Attrs.lat         -> attribute(_.n(String.valueOf(busStop.position.lat))),
        Table.Attrs.lng         -> attribute(_.n(String.valueOf(busStop.position.long))),
        Table.Attrs.x           -> attribute(_.n(String.valueOf(busStop.position.x))),
        Table.Attrs.y           -> attribute(_.n(String.valueOf(busStop.position.y)))
      ).asJava
    }
      .map(item => PutRequest.builder().item(item).build())
      .map(putReq => WriteRequest.builder().putRequest(putReq).build())
      .chunkN(25, allowFewer = true)
      .map(chunk => java.util.Map.of(Table.name, chunk.toList.asJava))
      .map(writeReq =>
        BatchWriteItemRequest
          .builder()
          .requestItems(writeReq)
          .build()
      )
      .evalMap(batchReq => IO(client.batchWriteItem(batchReq)).fromCompletable)
  }


  def findBusStopByCode(code: Int): IO[Option[BusStop]] = {
    val values = Map("code" -> attribute(_.n(String.valueOf(code)))).asJava

    val request = GetItemRequest
      .builder()
      .tableName(Table.name)
      .key(values)
      .build()

    for {
      //      _      <- log.debug(s"Getting busStop $id")
      result       <- IO(client.getItem(request)).fromCompletable
      mappedBusStop = Option(result.item())
                        .traverse(item => dynamoItemToBusStop(item))
      busStop      <- IO.fromTry(mappedBusStop)
    } yield busStop
  }

  def count: IO[Long] =
    IO(client.describeTable((b: DescribeTableRequest.Builder) => b.tableName(Table.name))).fromCompletable
      .map(resp => resp.table())
      .map(table => table.itemCount())

  def findBusStopByName(name: String): IO[List[BusStop]] = {
    val attributes = Map("#nameKey" -> "name")
    val values     = Map(":nameValue" -> attribute(_.s(name.toUpperCase)))

    val queryRequest = QueryRequest
      .builder()
      .tableName(Table.name)
      .indexName(Table.searchIndexName)
      .consistentRead(false)
      .expressionAttributeNames(attributes.asJava)
      .expressionAttributeValues(values.asJava)
      .keyConditionExpression("#nameKey = :nameValue")
      .build()

    for {
      //_      <- log.debug(s"Searching busStops $name")
      result        <- IO(client.query(queryRequest)).fromCompletable
      mappedBusStops = Option(result.items()).toList
                         .flatMap(_.asScala)
                         .traverse(item => dynamoItemToBusStop(item))
      busStop       <- IO.fromTry(mappedBusStops)
    } yield busStop
  }

  private def dynamoItemToBusStop(item: util.Map[String, AttributeValue]) =
    Try {
      BusStop(
        code = item.get(Table.indexHashKey).n().toInt,
        name = item.get(Table.Attrs.busStopName).s(),
        location = item.get(Table.Attrs.location).s(),
        comune = item.get(Table.Attrs.comune).s(),
        areaCode = item.get(Table.Attrs.areaCode).n().toInt,
        position = Position(
          x = item.get(Table.Attrs.x).n().toLong,
          y = item.get(Table.Attrs.y).n().toLong,
          lat = item.get(Table.Attrs.lat).n().toDouble,
          long = item.get(Table.Attrs.lng).n().toDouble
        )
      )
    }

  private def attribute(b: AttributeValue.Builder => Unit): AttributeValue = {
    val builder = AttributeValue.builder()
    b(builder)
    builder.build()
  }

}

object BusStopRepository {

  private case object Table {
    val name            = "bus_stops"
    val indexHashKey    = "code"
    val searchIndexName = "name-index"

    case object Attrs {
      val busStopName = "name"
      val location    = "location"
      val comune      = "comune"
      val areaCode    = "areaCode"
      val lat         = "lat"
      val lng         = "lng"
      val x           = "x"
      val y           = "y"
    }
  }

  def apply(awsClient: DynamoDbAsyncClient)(implicit cs: ContextShift[IO]): BusStopRepository = new BusStopRepository(
    awsClient
  )

  def makeResource(implicit cs: ContextShift[IO]): Resource[IO, BusStopRepository] = {
    val client = IO.fromTry(awsDefaultClient.orElse(clientFromEnv))

    Resource
      .fromAutoCloseable(client)
      .map(BusStopRepository(_))
  }

  def fromAWS()(implicit cs: ContextShift[IO]): Try[BusStopRepository] =
    awsDefaultClient.map(BusStopRepository(_))

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
