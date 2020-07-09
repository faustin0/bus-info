import java.time.format.DateTimeFormatter

import cats.effect.IO
import models.{BusRequest, HelloBusResponse}
import org.http4s.Method._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.scalaxml._
import org.http4s.{Headers, MediaType, Request, UrlForm}

import scala.xml.Elem

//val URL_HELLO_BUS = "QueryHellobus"
//val CONTENT_TYPE = "application/x-www-form-urlencoded"

case class HelloBusClient(private val httpClient: Client[IO]) {
  private val dateTimePattern = DateTimeFormatter.ofPattern("HHmm")

  private val targetUri =
    uri"https://hellobuswsweb.tper.it/web-services/hello-bus.asmx/QueryHellobus"

  def hello(busRequest: BusRequest): IO[HelloBusResponse] = {

    val request = Request[IO](
      method = POST,
      uri = targetUri,
      headers = Headers.of(Accept(MediaType.application.xml))
    ).withEntity(
      UrlForm(
        "fermata" -> busRequest.busStop.toString,
        "linea" -> busRequest.busID,
        "oraHHMM" -> busRequest.hour.format(dateTimePattern)
      )
    )

    for {
      xmlResponse <- httpClient.expect[Elem](request)
      parsed <- IO.fromEither(HelloBusResponse.fromXml(xmlResponse))
    } yield parsed
  }

  //TODO remove test method
  def sample(): IO[String] = {
    val target = uri"https://jsonplaceholder.typicode.com/todos/1"
    val request = GET(target)
    httpClient.expect[String](request)
  }
}

object HelloBusClient {
  def apply(httpClient: Client[IO]): HelloBusClient =
    new HelloBusClient(httpClient)
}
