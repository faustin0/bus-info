package dev.faustin0.importer.domain

import dev.faustin0.domain.{ BusStop, Position }
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.xml.Elem

class BusStopDecoderTest extends AnyFunSuite with Matchers with EitherValues {

  val busStop: Elem =
    <Table>
      <codice>1</codice>
      <denominazione>STAZIONE CENTRALE</denominazione>
      <ubicazione>PIAZZA MEDAGLIE D`ORO (PENSILINA C)</ubicazione>
      <comune>BOLOGNA</comune>
      <coordinata_x>686344</coordinata_x>
      <coordinata_y>930918</coordinata_y>
      <latitudine>44.505762</latitudine>
      <longitudine>11.343174</longitudine>
      <codice_zona>500</codice_zona>
    </Table>

  test("should parse a busStop xml entry") {
    val expected = BusStop(
      1,
      "STAZIONE CENTRALE",
      "PIAZZA MEDAGLIE D ORO (PENSILINA C)",
      "BOLOGNA",
      500,
      Position(686344, 930918, 44.505762, 11.343174)
    )

    val parsed = BusStopDecoder.decode(busStop)

    parsed.value shouldBe expected
  }

}
