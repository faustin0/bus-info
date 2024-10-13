package dev.faustin0.runtime.headers

import org.http4s._
import org.typelevel.ci._

/** Error type that the runtime encountered */
final private[runtime] class `Lambda-Runtime-Function-Error-Type`(val value: String)

private[runtime] object `Lambda-Runtime-Function-Error-Type` {
  val name = ci"Lambda-Runtime-Function-Error-Type"

  def apply(value: String) = new `Lambda-Runtime-Function-Error-Type`(value)

  private[headers] def parse(s: String): ParseResult[`Lambda-Runtime-Function-Error-Type`] =
    ParseResult.success(`Lambda-Runtime-Function-Error-Type`(s))

  implicit val headerInstance: Header[`Lambda-Runtime-Function-Error-Type`, Header.Single] =
    Header.createRendered(name, _.value, parse)

}
