package dev.faustin0.runtime.headers

import org.http4s._
import org.typelevel.ci._

/**
 * X-Ray tracing header.
 */
private[runtime] final class `Lambda-Runtime-Trace-Id`(val value: String)

private[runtime] object `Lambda-Runtime-Trace-Id` {

  def apply(value: String) = new `Lambda-Runtime-Trace-Id`(value)

  final val name = ci"Lambda-Runtime-Trace-Id"

  private[headers] def parse(s: String): ParseResult[`Lambda-Runtime-Trace-Id`] =
    ParseResult.success(`Lambda-Runtime-Trace-Id`(s))

  implicit val headerInstance: Header[`Lambda-Runtime-Trace-Id`, Header.Single] =
    Header.createRendered(name, _.value, parse)
}
