package izumi.fundamentals.platform.time

import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneId, ZonedDateTime}
import izumi.fundamentals.platform.time.IzTime.TZ_UTC

final class IzLongParsers(private val t: Long) extends AnyVal {
  def asEpochSecondsLocal: LocalDateTime = {
    val instant = Instant.ofEpochSecond(t)
    LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
  }

  def asEpochMillisLocal: LocalDateTime = {
    val instant = Instant.ofEpochMilli(t)
    LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
  }

  def asEpochSecondUtcZoned: ZonedDateTime = {
    val instant = Instant.ofEpochSecond(t)
    ZonedDateTime.ofInstant(instant, TZ_UTC)
  }

  def asEpochMillisUtcZoned: ZonedDateTime = {
    val instant = Instant.ofEpochMilli(t)
    ZonedDateTime.ofInstant(instant, TZ_UTC)
  }

  def asEpochSecondsUtcOffset: OffsetDateTime = {
    val instant = Instant.ofEpochSecond(t)
    OffsetDateTime.ofInstant(instant, TZ_UTC)
  }

  def asEpochMillisUtcOffset: OffsetDateTime = {
    val instant = Instant.ofEpochMilli(t)
    OffsetDateTime.ofInstant(instant, TZ_UTC)
  }

  @deprecated("use asEpochSecondUtcZoned")
  def asEpochSecondsUtc: ZonedDateTime = {
    val instant = Instant.ofEpochSecond(t)
    ZonedDateTime.ofInstant(instant, TZ_UTC)
  }

  @deprecated("use asEpochMillisUtcZoned")
  def asEpochMillisUtc: ZonedDateTime = {
    val instant = Instant.ofEpochMilli(t)
    ZonedDateTime.ofInstant(instant, TZ_UTC)
  }
}
