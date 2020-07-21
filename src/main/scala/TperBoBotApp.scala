import java.time.LocalTime

import cats.data.Validated
import cats.effect.{ExitCode, IO, IOApp}
import io.circe.generic.auto._
import models.{BusRequest, Invalid, NoBus, Successful}
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{Logger => ClientLogger}
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.{HttpRoutes, ParseResult, QueryParamDecoder}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class Routes(helloBusClient: HelloBusClient) {
  val helloBusService = HttpRoutes
    .of[IO] {
      case GET -> Root / "hello" / IntVar(busStop) :? BusNumber(bus) +& Hour(hour) =>
        hour match {
          case Some(Validated.Valid(h))   => execRequest(BusRequest(busStop, bus, Some(h)))
          case Some(Validated.Invalid(e)) => BadRequest(e.map(_.sanitized))
          case None                       => execRequest(BusRequest(busStop, bus))
        }

      case GET -> Root / "hello" / "" =>
        BadRequest(Invalid("missing busStop path"))

      case GET -> Root / "hello" / invalid =>
        BadRequest(Invalid(s"Invalid busStop: $invalid"))
    }
    .orNotFound

  private val execRequest = (busRequest: BusRequest) => {
    for {
      resp <- helloBusClient.hello(busRequest)
      restResp <- resp match {
        case r: NoBus      => NotFound(r)
        case r: Invalid    => BadRequest(r)
        case r: Successful => Ok(r.buses)
      }
    } yield restResp
  }

  object BusNumber extends OptionalQueryParamDecoderMatcher[String]("bus")
  object Hour extends OptionalValidatingQueryParamDecoderMatcher[LocalTime]("hour")

  implicit val timeQueryDecoder: QueryParamDecoder[LocalTime] =
    QueryParamDecoder[String].emap(
      h => ParseResult.fromTryCatchNonFatal(s"Invalid date format '$h'")(LocalTime.parse(h))
    )

}

object TperBoBotApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val routes = BlazeClientBuilder[IO](global)
      .withConnectTimeout(5 seconds)
      .withRequestTimeout(7 seconds)
      .resource
      .map(client => ClientLogger(logHeaders = false, logBody = true)(client))
      .map(client => HelloBusClient(client))
      .map(tperClient => new Routes(tperClient))

    routes
      .use(routes => {
        val app = routes.helloBusService
        val loggedApp = Logger.httpApp(logHeaders = false, logBody = true)(app)

        BlazeServerBuilder[IO](global)
          .bindHttp(8080, "localhost")
          .withHttpApp(loggedApp)
          .serve
          .compile
          .drain
      })
      .as(ExitCode.Success)
  }
}
