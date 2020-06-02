package models

import java.time.LocalTime


case class BusRequest(
                       busID: String = "",
                       busStop: Int,
                       hour: LocalTime = LocalTime.now()
                     )
