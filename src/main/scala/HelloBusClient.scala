import java.time.format.DateTimeFormatter

import cats.effect.IO
import models.{BusInfoResponse, BusRequest}
import org.http4s.Method._
import org.http4s.client._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.scalaxml._
import org.http4s.{Headers, MediaType, Request, UrlForm}

import scala.xml.Elem

case class HelloBusClient(private val httpClient: Client[IO]) {
  private val dateTimePattern = DateTimeFormatter.ofPattern("HHmm")

  private val targetUri =
    uri"https://hellobuswsweb.tper.it/web-services/hello-bus.asmx/QueryHellobus"

  def hello(busRequest: BusRequest): IO[BusInfoResponse] = {
    val request = createHttpRequest(busRequest)

    for {
      xmlResponse <- httpClient.expect[Elem](request)
      parsedResponse <- IO.fromEither(BusInfoResponse.fromXml(xmlResponse))
    } yield parsedResponse
  }

  private def createHttpRequest(busRequest: BusRequest) = {
    Request[IO](
      method = POST,
      uri = targetUri,
      headers = Headers.of(
        Accept(MediaType.application.xml),
        `Content-Type`(MediaType.application.`x-www-form-urlencoded`)
      )
    ).withEntity(
      UrlForm(
        "fermata" -> busRequest.busStop.toString,
        "linea" -> busRequest.busID.getOrElse(""),
        "oraHHMM" -> busRequest.hour
          .map(_.format(dateTimePattern))
          .getOrElse("")
      )
    )
  }
}

object HelloBusClient {
  def apply(httpClient: Client[IO]): HelloBusClient =
    new HelloBusClient(httpClient)
}
