package dev.faustin0

import cats.effect.IO
import dev.faustin0.domain.{ BusInfoResponse, BusRequest }
import org.http4s.Method._
import org.http4s.client._
import org.http4s.client.middleware.{ Logger => ClientLogger }
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.scalaxml._
import org.http4s.{ Headers, MediaType, Request, UrlForm }
import org.typelevel.log4cats.slf4j.Slf4jLogger

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
  private val logger = Slf4jLogger.getLogger[IO]

  private val dateTimePattern = DateTimeFormatter.ofPattern("HHmm")

  private val targetUri       =
    uri"https://hellobuswsweb.tper.it/web-services/hello-bus.asmx/QueryHellobus"

  def apply(httpClient: Client[IO]) = new HelloBusClient(httpClient)

  def withLogging(httpClient: Client[IO]): HelloBusClient = {

    val logAction: String => IO[Unit] = logger.info(_)
    new HelloBusClient(ClientLogger(logHeaders = false, logBody = true, logAction = Some(logAction))(httpClient))
  }

}
