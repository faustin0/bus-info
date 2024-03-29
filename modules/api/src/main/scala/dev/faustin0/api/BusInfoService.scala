package dev.faustin0.api

import cats.Applicative
import cats.data.OptionT
import cats.effect.IO
import dev.faustin0.HelloBusClient
import dev.faustin0.domain._
import dev.faustin0.repositories.DynamoBusStopRepository

trait BusInfoDSL[F[_]] {

  def getBusStop(busStopCode: Int): F[Either[String, BusStop]]

  def searchBusStop(busStopName: String): F[List[BusStop]]

  def getNextBuses(busRequest: BusRequest): F[BusInfoResponse]
}

class BusInfoService private (
  private val client: HelloBusClient,
  private val repo: BusStopRepository[IO]
) extends BusInfoDSL[IO] {

  override def getBusStop(busStopCode: Int): IO[Either[String, BusStop]] =
    repo
      .findBusStopByCode(busStopCode)
      .map(_.toRight(left = s"no bus stop with code $busStopCode"))

  override def getNextBuses(busRequest: BusRequest): IO[BusInfoResponse] =
    OptionT(repo.findBusStopByCode(busRequest.busStop))
      .semiflatMap(_ => client.hello(busRequest))
      .getOrElse(BusStopNotHandled(s"${busRequest.busStop} not handled"))

  override def searchBusStop(busStopName: String): IO[List[BusStop]] =
    repo.findBusStopByName(busStopName)

}

object BusInfoService {
  def apply(client: HelloBusClient, repo: DynamoBusStopRepository): BusInfoService = new BusInfoService(client, repo)
}

case class InMemoryBusInfoService[F[_]: Applicative]() extends BusInfoDSL[F] {

  private val stops = Map(
    303 -> BusStop(303, "IRNERIO", "location", "Bologna", 65, Position(0, 0, 0, 0))
  )

  private val busStops = Map(
    150 -> List(BusResponse(303, "27A", true, "14:30"))
  )

  override def getBusStop(busStopCode: Int): F[Either[String, BusStop]] =
    Applicative[F].pure(stops.get(busStopCode).toRight("missing stop"))

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
