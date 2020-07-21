package models

import java.time.LocalTime

case class BusRequest(
  busStop: Int,
  busID: Option[String] = None,
  hour: Option[LocalTime] = None
)

object BusRequest {

  def apply(busStop: Int, busID: String): BusRequest = {
    new BusRequest(busStop, Some(busID))
  }

  def apply(busStop: Int, busID: String, hour: LocalTime): BusRequest = {
    new BusRequest(busStop, Some(busID), Some(hour))
  }
}
