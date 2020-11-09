import cats.Applicative
import cats.data.OptionT
import cats.effect.IO
import models._
import repositories.BusStopRepository

trait BusInfoDSL[F[_]] {

  def findBusStop(busStopCode: Int): OptionT[F, BusStop]

  def getNextBuses(busRequest: BusRequest): F[BusInfoResponse]
}

class BusInfoService private (
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

object BusInfoService {
  def apply(client: HelloBusClient, repo: BusStopRepository): BusInfoService = new BusInfoService(client, repo)
}

case class InMemoryBusInfoService[F[_]: Applicative]() extends BusInfoDSL[F] {
  private val stops = Map(
    303 -> BusStop(303, "stopName", "location", "Bologna", 65, Position(0, 0, 0, 0))
  )

  private val busStope = Map(
    150 -> List(BusResponse("27A", true, "14:30"))
  )
  override def findBusStop(busStopCode: Int): OptionT[F, BusStop] =
    OptionT.fromOption[F](stops.get(busStopCode))

  override def getNextBuses(busRequest: BusRequest): F[BusInfoResponse] =
    Applicative[F].pure(busStope.get(busRequest.busStop).fold[BusInfoResponse](BusStopNotHandled("coglionazzo"))(l => Successful(l)))
}
