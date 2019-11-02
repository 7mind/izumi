package izumi.fundamentals.platform.time

import java.time.OffsetDateTime
import java.util.Date

import izumi.fundamentals.platform.time.IzTime.TZ_UTC

final class IzDate(private val value: Date) extends AnyVal {
  def toTsAsUtc: OffsetDateTime = value.toInstant.atZone(TZ_UTC).toOffsetDateTime

  def <=(other: Date): Boolean = {
    value.equals(other) || value.before(other)
  }

  def >=(other: Date): Boolean = {
    value.equals(other) || value.before(other)
  }

  def <(other: Date): Boolean = {
    value.before(other)
  }

  def >(other: Date): Boolean = {
    value.after(other)
  }
}
