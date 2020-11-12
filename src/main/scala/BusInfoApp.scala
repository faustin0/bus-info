import cats.effect.{ ExitCode, IO, IOApp, Resource }
import repositories.BusStopRepository

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

object BusInfoApp extends IOApp {
  private val cachedEc = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  override def run(args: List[String]): IO[ExitCode] = {
    val application: Resource[IO, Application] = for {
      tperClient    <- HelloBusClient.make("hellobuswsweb.tper.it", cachedEc)
      busStopRepo   <- BusStopRepository.make
      busInfoService = BusInfoService(tperClient, busStopRepo)
    } yield Application(busInfoService)(global)

    application.use(_.run).as(ExitCode.Success)
  }
}
