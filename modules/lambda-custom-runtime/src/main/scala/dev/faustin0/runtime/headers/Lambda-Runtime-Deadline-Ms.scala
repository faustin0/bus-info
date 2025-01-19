package dev.faustin0.runtime.headers

import org.http4s._
import org.typelevel.ci._

/** Function execution deadline counted in milliseconds since the Unix epoch.
  */
final private[runtime] class `Lambda-Runtime-Deadline-Ms`(val value: Long)

private[runtime] object `Lambda-Runtime-Deadline-Ms` {

  def apply(value: Long) = new `Lambda-Runtime-Deadline-Ms`(value)

  final val name = ci"Lambda-Runtime-Deadline-Ms"

  private[headers] def parse(s: String): ParseResult[`Lambda-Runtime-Deadline-Ms`] =
    s.toLongOption.toRight(ParseFailure(name.toString, s)).map(`Lambda-Runtime-Deadline-Ms`(_))

  implicit val headerInstance: Header[`Lambda-Runtime-Deadline-Ms`, Header.Single] =
    Header.createRendered(name, _.value, parse)

}
