package izumi.fundamentals.platform.time

import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor

final class IzOptionalTimeParsers(private val value: Option[String]) extends AnyVal {
  import IzTime._
  def mapToTemporal: Option[TemporalAccessor] = value.map(_.toTemporal)

  def mapToTsZ: Option[ZonedDateTime] = value.map(_.toTsZ)
}
