package dev.faustin0.runtime.headers

import org.http4s._
import org.typelevel.ci._

/**
 * AWS request ID associated with the request.
 */
private[runtime] final class `Lambda-Runtime-Aws-Request-Id`(val value: String)

private[runtime] object `Lambda-Runtime-Aws-Request-Id` {

  def apply(value: String) = new `Lambda-Runtime-Aws-Request-Id`(value)

  final val name = ci"Lambda-Runtime-Aws-Request-Id"

  private[headers] def parse(s: String): ParseResult[`Lambda-Runtime-Aws-Request-Id`] =
    ParseResult.success(`Lambda-Runtime-Aws-Request-Id`(s))

  implicit val headerInstance: Header[`Lambda-Runtime-Aws-Request-Id`, Header.Single] =
    Header.createRendered(name, _.value, parse)
}
