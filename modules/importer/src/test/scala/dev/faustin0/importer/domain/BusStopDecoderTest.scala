package dev.faustin0.importer.domain

import dev.faustin0.domain.{ BusStop, Position }
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.xml.Elem

class BusStopDecoderTest extends AnyFunSuite with Matchers with EitherValues {

  test("should parse a busStop xml entry") {
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

    val expected = BusStop(
      code = 1,
      name = "STAZIONE CENTRALE",
      location = "PIAZZA MEDAGLIE D`ORO (PENSILINA C)",
      comune = "BOLOGNA",
      areaCode = 500,
      position = Position(686344, 930918, 44.505762, 11.343174)
    )

    val parsed = BusStopDecoder.decode(busStop)

    parsed.value shouldBe expected
  }

  test("should replace A` with A") {
    val busStop: Elem =
      <Table>
        <codice>1</codice>
        <denominazione>CITTA` DEL RAGAZZO</denominazione>
        <ubicazione>xxx</ubicazione>
        <comune>BOLOGNA</comune>
        <coordinata_x>686344</coordinata_x>
        <coordinata_y>930918</coordinata_y>
        <latitudine>44.505762</latitudine>
        <longitudine>11.343174</longitudine>
        <codice_zona>500</codice_zona>
      </Table>

    val expected = BusStop(
      code = 1,
      name = "CITTA DEL RAGAZZO",
      location = "xxx",
      comune = "BOLOGNA",
      areaCode = 500,
      position = Position(686344, 930918, 44.505762, 11.343174)
    )

    val parsed = BusStopDecoder.decode(busStop)

    parsed.value shouldBe expected
  }

  test("should remove '[' ']' content") {
    val busStop: Elem =
      <Table>
        <codice>600649</codice>
        <denominazione>CONA TAMBELLINA[919]</denominazione>
        <ubicazione>VIA TAMBELLINA 197</ubicazione>
        <comune>CONA</comune>
        <coordinata_x>686344</coordinata_x>
        <coordinata_y>930918</coordinata_y>
        <latitudine>44.505762</latitudine>
        <longitudine>11.343174</longitudine>
        <codice_zona>500</codice_zona>
      </Table>

    val expected = BusStop(
      code = 600649,
      name = "CONA TAMBELLINA",
      location = "VIA TAMBELLINA 197",
      comune = "CONA",
      areaCode = 500,
      position = Position(686344, 930918, 44.505762, 11.343174)
    )

    val parsed = BusStopDecoder.decode(busStop)

    parsed.value shouldBe expected
  }

  test("should remove '-'") {
    val busStop: Elem =
      <Table>
        <codice>600657</codice>
        <denominazione>PONTEGRADELLA ~ PASSERELL</denominazione>
        <ubicazione>FERRARA - FORMIGNANA 137</ubicazione>
        <comune>FERRARA</comune>
        <coordinata_x>686344</coordinata_x>
        <coordinata_y>930918</coordinata_y>
        <latitudine>44.505762</latitudine>
        <longitudine>11.343174</longitudine>
        <codice_zona>500</codice_zona>
      </Table>

    val expected = BusStop(
      code = 600657,
      name = "PONTEGRADELLA PASSERELL",
      location = "FERRARA - FORMIGNANA 137",
      comune = "FERRARA",
      areaCode = 500,
      position = Position(686344, 930918, 44.505762, 11.343174)
    )

    val parsed = BusStopDecoder.decode(busStop)

    parsed.value shouldBe expected
  }

}
