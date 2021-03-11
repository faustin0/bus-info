package dev.faustin0.domain

import fs2.Pipe

import scala.util.control.NoStackTrace

case class FailureReason(reason: Throwable) extends NoStackTrace

trait BusStopRepository[F[_]] {
  def insert(busStop: BusStop): F[Unit]

  def batchInsert: Pipe[F, BusStop, FailureReason]

  def findBusStopByCode(code: Int): F[Option[BusStop]]

  def count: F[Long]

  def findBusStopByName(name: String): F[List[BusStop]]
}
