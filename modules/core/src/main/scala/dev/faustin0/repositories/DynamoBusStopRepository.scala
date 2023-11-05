package dev.faustin0.repositories

import cats.effect.std.Dispatcher
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.http4s.syntax.all._
import dev.faustin0.Utils.JavaFutureOps
import dev.faustin0.domain.{ BusStop, BusStopRepository, FailureReason }
import fs2.concurrent.Channel
import fs2.{ Stream, _ }
import org.http4s.{ Header, Headers, Method, Query, Request, Uri }
import org.http4s.client.Client
import org.reactivestreams.{ Subscriber, Subscription }
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.http.async.{ AsyncExecuteRequest, SdkAsyncHttpClient }
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import scala.jdk.CollectionConverters._
import scala.jdk.FunctionConverters._
import scala.util.{ Failure, Success }

class DynamoBusStopRepository private (client: DynamoDbAsyncClient)(implicit L: Logger[IO])
    extends BusStopRepository[IO] {

  override def insert(busStop: BusStop): IO[Unit] = {
    val request = PutItemRequest
      .builder()
      .tableName(BusStopTable.name)
      .item(BusStopTable.busStopToDynamoMapping(busStop))
      .build()

    L.debug(s"Inserting bus-stop $busStop") *>
      IO(client.putItem(request)).fromCompletable.void
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
      _            <- L.debug(s"Getting busStop $code")
      result       <- IO(client.getItem(request)).fromCompletable
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

    IO(client.describeTable(tableDesc)).fromCompletable
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
      result  <- IO(client.query(queryRequest)).fromCompletable
      busStop <- Option(result.items()).toList
                   .flatMap(_.asScala)
                   .traverse(item => BusStopTable.dynamoItemToBusStop(item))
                   .liftTo[IO]
    } yield busStop
  }

}

object DynamoBusStopRepository {

  def apply(awsClient: DynamoDbAsyncClient, l: Logger[IO]): DynamoBusStopRepository =
    new DynamoBusStopRepository(awsClient)(l)

  def makeResource(httpClient: EmberAsyncHttpClient, l: Logger[IO]): Resource[IO, DynamoBusStopRepository] = {
    // todo remove this shit
    val client = awsDefaultClient(httpClient).orElse(clientFromEnv)

    Resource
      .fromAutoCloseable(client)
      .map(c => DynamoBusStopRepository(c, l))
  }

  def fromAWS(httpClient: EmberAsyncHttpClient)(implicit l: Logger[IO]): IO[DynamoBusStopRepository] =
    awsDefaultClient(httpClient).map(DynamoBusStopRepository(_, l))

  @Deprecated
  def fromAWS(implicit l: Logger[IO]): IO[DynamoBusStopRepository] =
    IO(DynamoDbAsyncClient.create()).map(DynamoBusStopRepository(_, l))

  private def awsDefaultClient(httpClient: EmberAsyncHttpClient): IO[DynamoDbAsyncClient] =
    IO(DynamoDbAsyncClient.builder().httpClient(httpClient).build())

  private def clientFromEnv: IO[DynamoDbAsyncClient] =
    IO {
      DynamoDbAsyncClient
        .builder()
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .region(Region.EU_CENTRAL_1)
        .endpointOverride(
          URI.create("http://localhost:4567") // TODO parametrize endpoint
        )
        .build()
    }

  class EmberAsyncHttpClient(client: Client[IO], dispatcher: Dispatcher[IO]) extends SdkAsyncHttpClient {

    override def execute(request: AsyncExecuteRequest): CompletableFuture[Void] = {
      val adaptedRequest = for {
        channel  <- Channel.unbounded[IO, Byte]
        _         = request
                      .requestContentPublisher()
                      .subscribe(new Subscriber[ByteBuffer] {
                        override def onSubscribe(s: Subscription): Unit = println("onSubscribe")

                        override def onNext(t: ByteBuffer): Unit = {
                          println("onNext")
                          dispatcher.unsafeRunSync(
                            Stream
                              .chunk(Chunk.byteBuffer(t))
                              .covary[IO]
                              .through(channel.sendAll)
                              .compile
                              .drain
                          )
                        }

                        override def onError(t: Throwable): Unit = dispatcher.unsafeRunSync(IO.raiseError(t))

                        override def onComplete(): Unit = {
                          println("onComplete")
                          dispatcher.unsafeRunSync(channel.closed)
                        }
                      })
        uri       = Uri(
                      path = Uri.Path.unsafeFromString(request.request().getUri.toString).concat(
                        Uri.Path.unsafeFromString(request.request().encodedPath())
                      ) ,
                      query = adaptQuery(request)
                    )
        req       = Request.apply(
                      method = request.request().method() match {
                        case SdkHttpMethod.GET     => Method.GET
                        case SdkHttpMethod.POST    => Method.POST
                        case SdkHttpMethod.PUT     => Method.PUT
                        case SdkHttpMethod.DELETE  => Method.DELETE
                        case SdkHttpMethod.HEAD    => Method.HEAD
                        case SdkHttpMethod.PATCH   => Method.PATCH
                        case SdkHttpMethod.OPTIONS => Method.OPTIONS
                      },
                      uri = uri,
                      headers = Headers(
                        request
                          .request()
                          .headers()
                          .asScala
                          .map { case (k, values) => Header.Raw(CIString(k), values.get(0)) }
                          .toList
                      ),
                      body = channel.stream
                    )
        response <- client.run(req).use(resp => resp.as[String].flatMap(IO.println))
      } yield response

//      Stream.chunk(Chunk.byteBuffer())

      dispatcher
        .unsafeToCompletableFuture(adaptedRequest)
        .thenApply((t: Unit) => null) // adapting to void
    }

    private def adaptQuery(request: AsyncExecuteRequest): Query =
      request
        .request()
        .encodedQueryParameters()
        .map(Query.unsafeFromString)
        .orElseGet(new Supplier[Query] {
          override def get(): Query = Query.empty
        })

    override def close(): Unit = ()
  }

}
