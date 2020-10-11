import cats.data.OptionT
import cats.effect.IO
import models._
import repositories.BusStopRepository

case class BusInfoService(
                           private val client: HelloBusClient,
                           private val repo: BusStopRepository
                         ) {
  def findBusStop(busStopCode: Int): OptionT[IO, BusStop] =
    repo.findBusStopByCode(busStopCode)

  def getNextBuses(busRequest: BusRequest): IO[BusInfoResponse] = {
    repo
      .findBusStopByCode(busRequest.busStop)
      .semiflatMap(_ => client.hello(busRequest))
      .getOrElse(BusStopNotHandled(s"${busRequest.busStop} not handled"))
  }
}
