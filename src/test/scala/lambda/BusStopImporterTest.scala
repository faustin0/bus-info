package lambda

import models.{BusStop, Position}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.xml.{Elem, XML}

class BusStopImporterTest extends AnyFunSuite with Inside with Matchers {

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
      "PIAZZA MEDAGLIE D`ORO (PENSILINA C)",
      "BOLOGNA",
      500,
      Position(686344, 930918, 44.505762, 11.343174)
    )
    val parsed = BusStop.fromXml(busStop)

    inside(parsed) {
      case Right(busStop) => busStop.shouldBe(expected)
      case Left(e)        => fail(e)
    }
  }

  test("should parse all xml") {

    val xml = XML.loadFile(getClass.getResource("/fermate_20200701.xml").getFile)
    val stops = BusStopImporter.parseXmlDataset(xml)

    stops match {
      case Left(e)   => fail(e)
      case Right(bs) => bs should have size 9589
    }
  }

  test("should fail") {
    val xml = XML.loadString("<")
  }

}
