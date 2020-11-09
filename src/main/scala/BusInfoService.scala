import cats.Applicative
import cats.data.OptionT
import cats.effect.IO
import models._
import repositories.BusStopRepository

trait BusInfoDSL[F[_]] {

  def findBusStop(busStopCode: Int): OptionT[F, BusStop]

  def getNextBuses(busRequest: BusRequest): F[BusInfoResponse]
}

case class BusInfoService(
  private val client: HelloBusClient,
  private val repo: BusStopRepository
) extends BusInfoDSL[IO] {

  override def findBusStop(busStopCode: Int): OptionT[IO, BusStop] =
    repo.findBusStopByCode(busStopCode)

  override def getNextBuses(busRequest: BusRequest): IO[BusInfoResponse] =
    repo
      .findBusStopByCode(busRequest.busStop)
      .semiflatMap(_ => client.hello(busRequest))
      .getOrElse(BusStopNotHandled(s"${busRequest.busStop} not handled"))
}

case class InMemoryBusInfoService[F[_]: Applicative]() extends BusInfoDSL[F] {
  private val stops = Map(
    303 -> BusStop(303, "stopName", "location", "Bologna", 65, Position(0, 0, 0, 0))
  )
  override def findBusStop(busStopCode: Int): OptionT[F, BusStop] =
    OptionT.fromOption[F](stops.get(busStopCode))

  override def getNextBuses(busRequest: BusRequest): F[BusInfoResponse] =
    Applicative[F].pure(Successful(List(BusResponse("27A", true, "14:30"))))
}
