package izumi.fundamentals.platform.time

import izumi.fundamentals.platform.IzPlatformPureUtil

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField.{HOUR_OF_DAY, MINUTE_OF_HOUR, NANO_OF_SECOND, SECOND_OF_MINUTE}
import java.time.{LocalDateTime, OffsetDateTime}
import java.util.Date
import scala.concurrent.duration.Duration
import scala.language.implicitConversions

// safe to run on sjs with shims
trait IzTimeSafe extends IzPlatformPureUtil {
  @inline implicit final def toRichOffsetDateTime(timestamp: OffsetDateTime): IzOffsetDateTime = new IzOffsetDateTime(timestamp)
  @inline implicit final def toRichLocalDateTime(timestamp: LocalDateTime): IzLocalDateTime = new IzLocalDateTime(timestamp)
  @inline implicit final def toRichDate(value: Date): IzDate = new IzDate(value)
  @inline implicit final def toRichDuration(duration: Duration): IzDuration = new IzDuration(duration)

  // formatters with 3 decimal positions for nanos
  final lazy val ISO_LOCAL_DATE_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder().parseCaseInsensitive
      .append(ISO_LOCAL_DATE)
      .appendLiteral('T')
      .append(ISO_LOCAL_TIME_3NANO)
      .toFormatter()
  }

  final lazy val ISO_LOCAL_DATE: DateTimeFormatter = {
    DateTimeFormatter.ISO_LOCAL_DATE
  }

  final lazy val ISO_LOCAL_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder()
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .optionalStart
      .appendLiteral(':')
      .appendValue(SECOND_OF_MINUTE, 2)
      .optionalStart
      .appendFraction(NANO_OF_SECOND, 3, 3, true)
      .toFormatter()
  }

  final lazy val ISO_OFFSET_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder().parseCaseInsensitive
      .append(ISO_LOCAL_TIME_3NANO)
      .appendOffsetId
      .toFormatter()
  }

  final val ISO_DATE_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder().parseCaseInsensitive
      .append(ISO_LOCAL_DATE_TIME_3NANO)
      .appendOffsetId
      .optionalStart
      .appendLiteral('[')
      .parseCaseSensitive()
      .appendZoneRegionId()
      .appendLiteral(']')
      .toFormatter()
  }

  final val ISO_DATE = {
    DateTimeFormatter.ISO_DATE
  }

  final val ISO_TIME_3NANO = {
    new DateTimeFormatterBuilder().parseCaseInsensitive
      .append(ISO_LOCAL_TIME_3NANO)
      .optionalStart
      .appendOffsetId
      .toFormatter
  }
}

object IzTimeSafe extends IzTimeSafe with IzTimeOrderingSafe
