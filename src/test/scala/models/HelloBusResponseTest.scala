package models

import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.xml.Elem

class HelloBusResponseTest extends AnyFunSuite with Inside with Matchers {

  val successfulResponse: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      TperHellobus: 28 DaSatellite 09:07 (Bus5517 CON PEDANA), 28 DaSatellite 09:20 (Bus5566 CON PEDANA)
    </string>

  val successfulResponseWithHour: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      TperHellobus: (x21:40) 27A Previsto 21:59 (Bus6567 CON PEDANA), 27B Previsto 22:29 (Bus6584 CON PEDANA)
    </string>

  val HellobusHelp: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      HellobusHelp: LINEA ASD NON GESTITA, indicare FERMATA LINEA o FERMATA LINEA ORA. Esempi: 4004 27A , 4004 27A 0810
    </string>

  val noBus: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      TperHellobus: OGGI NESSUNA ALTRA CORSA DI 1 PER FERMATA 303
    </string>

  test("should parse xml from tper into successful response ") {

    val r1 = BusResponse("28", true, "09:07", "(Bus5517 CON PEDANA)")
    val r2 = BusResponse("28", true, "09:20", "(Bus5566 CON PEDANA)")
    val expected = Successful(List(r1, r2))
    val result = HelloBusResponse.fromXml(successfulResponse)

    inside(result) {
      case Right(response) =>
        response.shouldBe(expected)
    }
  }

  test(
    "should parse xml from tper into successful response when hour is present"
  ) {

    val r1 = BusResponse("27A", false, "21:59", "(Bus6567 CON PEDANA)")
    val r2 = BusResponse("27B", false, "22:29", "(Bus6584 CON PEDANA)")
    val expected = Successful(List(r1, r2))
    val result = HelloBusResponse.fromXml(successfulResponseWithHour)

    inside(result) {
      case Right(response) =>
        response.shouldBe(expected)
    }
  }

  test("should parse helloBusHelp into Invalid response") {

    val expected = Invalid(
      "HellobusHelp: LINEA ASD NON GESTITA, indicare FERMATA LINEA o FERMATA LINEA ORA. Esempi: 4004 27A , 4004 27A 0810"
    )
    val result = HelloBusResponse.fromXml(HellobusHelp)

    inside(result) {
      case Right(response) =>
        response.shouldBe(expected)
    }
  }

  test("should parse response when no more buses into NoBus response") {

    val expected =
      NoBus("TperHellobus: OGGI NESSUNA ALTRA CORSA DI 1 PER FERMATA 303")
    val result = HelloBusResponse.fromXml(noBus)

    inside(result) {
      case Right(response) =>
        response.shouldBe(expected)
    }
  }

}
