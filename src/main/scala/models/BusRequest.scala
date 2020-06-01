package models

import java.time.LocalTime
import java.time.format.DateTimeFormatter


case class BusRequest(
                       busID: String = "",
                       busStop: Int,
                       hour: Option[LocalTime] = None
                     ) {
  private val dateTimePattern = DateTimeFormatter.ofPattern("HHmm")
}
