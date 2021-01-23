import cats.Applicative
import cats.data.OptionT
import cats.effect.IO
import models._
import repositories.BusStopRepository

trait BusInfoDSL[F[_]] {

  def getBusStop(busStopCode: Int): OptionT[F, BusStop]

  def searchBusStop(busStopName: String): F[List[BusStop]]

  def getNextBuses(busRequest: BusRequest): F[BusInfoResponse]
}

class BusInfoService private (
  private val client: HelloBusClient,
  private val repo: BusStopRepository
) extends BusInfoDSL[IO] {

  override def getBusStop(busStopCode: Int): OptionT[IO, BusStop] =
    repo.findBusStopByCode(busStopCode)

  override def getNextBuses(busRequest: BusRequest): IO[BusInfoResponse] =
    repo
      .findBusStopByCode(busRequest.busStop)
      .semiflatMap(_ => client.hello(busRequest))
      .getOrElse(BusStopNotHandled(s"${busRequest.busStop} not handled"))

  override def searchBusStop(busStopName: String): IO[List[BusStop]] =
    repo
      .findBusStopByName(busStopName)
      .compile
      .toList
}

object BusInfoService {
  def apply(client: HelloBusClient, repo: BusStopRepository): BusInfoService = new BusInfoService(client, repo)
}

case class InMemoryBusInfoService[F[_]: Applicative]() extends BusInfoDSL[F] {

  private val stops = Map(
    303 -> BusStop(303, "IRNERIO", "location", "Bologna", 65, Position(0, 0, 0, 0))
  )

  private val busStops = Map(
    150 -> List(BusResponse(303, "27A", true, "14:30"))
  )

  override def getBusStop(busStopCode: Int): OptionT[F, BusStop] =
    OptionT.fromOption[F](stops.get(busStopCode))

  override def getNextBuses(busRequest: BusRequest): F[BusInfoResponse] =
    Applicative[F].pure(
      busStops.get(busRequest.busStop).fold[BusInfoResponse](BusStopNotHandled("coglionazzo"))(l => Successful(l))
    )

  override def searchBusStop(busStopName: String): F[List[BusStop]] =
    Applicative[F].pure(
      stops.collect { case (_, stop) => stop }
        .find(_.name.equalsIgnoreCase(busStopName))
        .toList
    )
}
