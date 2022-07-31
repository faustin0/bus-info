package dev.faustin0

import cats.effect.{ IO, Resource }
import dev.faustin0.domain.{ BusInfoResponse, BusRequest }
import org.http4s.Method._
import org.http4s.client._
import org.http4s.client.middleware.{ Logger => ClientLogger }
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.scalaxml._
import org.http4s.{ Headers, MediaType, Request, UrlForm }

import java.time.format.DateTimeFormatter
import scala.xml.Elem

class HelloBusClient private (private val httpClient: Client[IO]) {

  def hello(busRequest: BusRequest): IO[BusInfoResponse] = {
    val request = createHttpRequest(busRequest)

    httpClient
      .run(request)
      .use { resp =>
        for {
          xmlResponse    <- resp.as[Elem]
          parsedResponse <- IO.fromEither(BusInfoResponse.fromXml(xmlResponse, busRequest.busStop))
        } yield parsedResponse
      }
  }

  private def createHttpRequest(busRequest: BusRequest): Request[IO] =
    Request[IO](
      method = POST,
      uri = HelloBusClient.targetUri,
      headers = Headers(
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

  private val targetUri =
    uri"https://hellobuswsweb.tper.it/web-services/hello-bus.asmx/QueryHellobus"

  def apply(httpClient: Client[IO]): HelloBusClient = new HelloBusClient(
    httpClient
  )

  def make(logAction: String => IO[Unit]): Resource[IO, HelloBusClient] =
    EmberClientBuilder
      .default[IO]
      .build
      .map(ClientLogger(logHeaders = false, logBody = true, logAction = Some(logAction)))
      .map(client => new HelloBusClient(client))

}
