package dev.faustin0.domain

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.xml.Elem

class BusInfoResponseTest extends AnyFunSuite with EitherValues with Matchers {

  private val successfulResponse: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      TperHellobus: 28 DaSatellite 09:07 (Bus5517 CON PEDANA), 28 DaSatellite 09:20 (Bus5566 CON PEDANA)
    </string>

  private val successfulResponseWithHour: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      TperHellobus: (x21:40) 27A Previsto 21:59 (Bus6567 CON PEDANA), 27B Previsto 22:29 (Bus6584 CON PEDANA)
    </string>

  private val HellobusHelp_noBus: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      HellobusHelp: LINEA ASD NON GESTITA, indicare FERMATA LINEA o FERMATA LINEA ORA. Esempi: 4004 27A , 4004 27A 0810
    </string>

  private val HellobusHelp_NoStop: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      HellobusHelp: FERMATA 3033 NON GESTITA, indicare FERMATA LINEA o FERMATA LINEA ORA. Esempi: 4004 27A , 4004 27A 0810
    </string>

  private val noBus: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      TperHellobus: OGGI NESSUNA ALTRA CORSA DI 1 PER FERMATA 303
    </string>

  private val estimated: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      TperHellobus: (x14:51) 20 Previsto 14:51, 28 Previsto 14:57
    </string>

  private val outOfService: Elem =
    <string xmlns="https://hellobuswsweb.tper.it/web-services/hellobus.asmx">
      INFORMAZIONI SUI BUS TEMPORANEAMENTE SOSPESE
    </string>

  test("should parse xml from tper into successful response ") {

    val busStopCode = 303
    val r1          = BusResponse(busStopCode, "28", true, "09:07", "(Bus5517 CON PEDANA)")
    val r2          = BusResponse(busStopCode, "28", true, "09:20", "(Bus5566 CON PEDANA)")
    val expected    = Successful(List(r1, r2))
    val result      = BusInfoResponse.fromXml(successfulResponse, busStopCode)

    result.value.shouldBe(expected)
  }

  test("should parse helloBusHelp into Bus NotHandled response") {

    val expected = BusNotHandled(
      "bus not handled"
    )
    val result   = BusInfoResponse.fromXml(HellobusHelp_noBus, 303)

    result.value.shouldBe(expected)
  }

  test("should parse helloBusHelp into BusStop NotHandled response") {

    val expected = BusStopNotHandled(
      "bus-stop not handled"
    )
    val result   = BusInfoResponse.fromXml(HellobusHelp_NoStop, 303)

    result.value.shouldBe(expected)
  }

  test("should parse xml from tper into successful response when hour is present") {

    val busStopCode = 2022
    val r1          = BusResponse(busStopCode, "27A", false, "21:59", "(Bus6567 CON PEDANA)")
    val r2          = BusResponse(busStopCode, "27B", false, "22:29", "(Bus6584 CON PEDANA)")
    val expected    = Successful(List(r1, r2))
    val result      = BusInfoResponse.fromXml(successfulResponseWithHour, busStopCode)

    result.value.shouldBe(expected)
  }

  test("should parse response when no more buses into NoBus response") {

    val expected = Successful(Nil)
    val result   = BusInfoResponse.fromXml(noBus, 2022)

    result.value.shouldBe(expected)
  }

  test("should parse estimated response") {

    val busStopCode = 1010
    val b1          = BusResponse(busStopCode, "20", false, "14:51")
    val b2          = BusResponse(busStopCode, "28", false, "14:57")

    val expected = Successful(List(b1, b2))
    val result   = BusInfoResponse.fromXml(estimated, busStopCode)

    result.value.shouldBe(expected)
  }

  test("should parse oos response") {

    val result   = BusInfoResponse.fromXml(outOfService, 14)
    val expected = Suspended("INFORMAZIONI SUI BUS TEMPORANEAMENTE SOSPESE")

    result.value.shouldBe(expected)
  }

}
