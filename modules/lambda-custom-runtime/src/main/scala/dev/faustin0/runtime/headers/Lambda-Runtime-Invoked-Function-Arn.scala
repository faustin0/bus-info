package dev.faustin0.runtime.headers

import org.http4s._
import org.typelevel.ci._

/** The ARN requested. This can be different in each invoke that executes the same version.
  */
final private[runtime] class `Lambda-Runtime-Invoked-Function-Arn`(val value: String)

private[runtime] object `Lambda-Runtime-Invoked-Function-Arn` {

  def apply(value: String) = new `Lambda-Runtime-Invoked-Function-Arn`(value)

  final val name = ci"Lambda-Runtime-Invoked-Function-Arn"

  private[headers] def parse(s: String): ParseResult[`Lambda-Runtime-Invoked-Function-Arn`] =
    ParseResult.success(`Lambda-Runtime-Invoked-Function-Arn`(s))

  implicit val headerInstance: Header[`Lambda-Runtime-Invoked-Function-Arn`, Header.Single] =
    Header.createRendered(name, _.value, parse)

}
