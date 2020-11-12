import java.time.format.DateTimeFormatter

import cats.effect.{ConcurrentEffect, IO, Resource}
import models.{BusInfoResponse, BusRequest}
import org.http4s.Method._
import org.http4s.client._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{Logger => ClientLogger}
import org.http4s.headers._
import org.http4s.scalaxml._
import org.http4s._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.xml.Elem

class HelloBusClient private (private val httpClient: Client[IO], uri: Uri) {

  def hello(busRequest: BusRequest): IO[BusInfoResponse] = {
    val request = createHttpRequest(busRequest)

    for {
      xmlResponse    <- httpClient.expect[Elem](request)
      parsedResponse <- IO.fromEither(BusInfoResponse.fromXml(xmlResponse, busRequest.busStop))
    } yield parsedResponse
  }

  private def createHttpRequest(busRequest: BusRequest): Request[IO] =
    Request[IO](
      method = POST,
      uri = uri,
      headers = Headers.of(
        Accept(MediaType.application.xml),
        `Content-Type`(MediaType.application.`x-www-form-urlencoded`)
      )
    ).withEntity(
      UrlForm(
        "fermata" -> busRequest.busStop.toString,
        "linea"   -> busRequest.busID.getOrElse(""),
        "oraHHMM" -> busRequest.hour
          .map(_.format(HelloBusClient.dateTimePattern))
          .getOrElse("")
      )
    )
}

object HelloBusClient {

  private val dateTimePattern = DateTimeFormatter.ofPattern("HHmm")

  private def targetUri(host: String): Uri =
    Uri.unsafeFromString("https://" + host + "/web-services/hello-bus.asmx/QueryHellobus")

  def apply(httpClient: Client[IO], uri: Uri): HelloBusClient = new HelloBusClient(httpClient, uri)

  def make(
    host: String,
    executionContext: ExecutionContext
  )(implicit ce: ConcurrentEffect[IO]): Resource[IO, HelloBusClient] =
    BlazeClientBuilder[IO](executionContext)
      .withConnectTimeout(5 seconds)
      .withRequestTimeout(7 seconds)
      .resource
      .map(client => ClientLogger(logHeaders = false, logBody = true)(client))
      .map(client => new HelloBusClient(client, targetUri(host)))
}
