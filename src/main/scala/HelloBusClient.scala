import cats.effect.IO
import models.{BusRequest, HelloBusResponse, TransformError}
import org.http4s.Method._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.{Headers, MediaType, Request, UrlForm}

import scala.xml.Elem
import org.http4s.scalaxml._


//val URL_HELLO_BUS = "QueryHellobus"
//val CONTENT_TYPE = "application/x-www-form-urlencoded"

case class HelloBusClient(private val httpClient: Client[IO]) {
  def hello(busRequest: BusRequest): IO[Either[TransformError, HelloBusResponse]] = {

    val target = uri"https://hellobuswsweb.tper.it/web-services/hello-bus.asmx/QueryHellobus"

    val request = Request[IO](
      method = POST,
      uri = target,
      headers = Headers.of(Accept(MediaType.application.xml))
    ).withEntity(UrlForm(
      "fermata" -> busRequest.busStop.toString,
      "linea" -> busRequest.busID,
      "oraHHMM" -> busRequest.hour.map(_.toString).getOrElse("")
    ))

    httpClient
      .expect[Elem](request)
      .map(HelloBusResponse.fromXml)
  }

  //TODO remove test method
  def sample(): IO[String] = {
    val target = uri"https://jsonplaceholder.typicode.com/todos/1"
    val request = GET(
      target
    )
    httpClient.expect[String](request)
  }
}

object HelloBusClient {
  def apply(httpClient: Client[IO]): HelloBusClient = new HelloBusClient(httpClient)
}
