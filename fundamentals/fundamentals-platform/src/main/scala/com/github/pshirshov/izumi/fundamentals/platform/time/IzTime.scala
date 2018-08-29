package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time._
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField.{HOUR_OF_DAY, MINUTE_OF_HOUR, NANO_OF_SECOND, SECOND_OF_MINUTE}
import java.util.Date

import scala.language.implicitConversions


trait IzTime {
  final val TZ_UTC: ZoneId = ZoneId.of("UTC")

  final val EPOCH_OFFSET = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0), TZ_UTC)
  final val EPOCH = ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), TZ_UTC)

  // extended operators
  implicit def toRichZonedDateTime(timestamp: ZonedDateTime): IzZonedDateTime = new IzZonedDateTime(timestamp)

  implicit def toRichOffsetDateTime(timestamp: OffsetDateTime): IzOffsetDateTime = new IzOffsetDateTime(timestamp)

  implicit def toRichLocalDateTime(timestamp: LocalDateTime): IzLocalDateTime = new IzLocalDateTime(timestamp)

  implicit def toRichDate(value: Date): IzDate = new IzDate(value)

  // parsers
  implicit def toRichLong(value: Long): IzLongParsers = new IzLongParsers(value)

  implicit def stringToParseableTime(value: String): IzTimeParsers = new IzTimeParsers(value)

  implicit def maybeStringToParseableTime(value: Option[String]): IzOptionalTimeParsers = new IzOptionalTimeParsers(value)

  // current time
  def utcNow: ZonedDateTime = ZonedDateTime.now(TZ_UTC)

  def utcNowOffset: ZonedDateTime = ZonedDateTime.now(TZ_UTC)

  def isoNow: String = utcNow.isoFormat


  // formatters with 3 decimal positions for nanos
  final lazy val ISO_LOCAL_DATE_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive
      .append(ISO_LOCAL_DATE)
      .appendLiteral('T')
      .append(ISO_LOCAL_TIME_3NANO)
      .toFormatter()
  }
  final lazy val ISO_ZONED_DATE_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder()
      .append(ISO_OFFSET_DATE_TIME_3NANO)
      .optionalStart
      .appendLiteral('[')
      .parseCaseSensitive
      .appendZoneRegionId
      .appendLiteral(']')
      .toFormatter
  }
  final lazy val ISO_OFFSET_DATE_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive
      .append(ISO_LOCAL_DATE_TIME_3NANO)
      .parseLenient
      .appendOffsetId
      .parseStrict
      .toFormatter
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
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive.append(ISO_LOCAL_TIME_3NANO)
      .appendOffsetId
      .toFormatter()
  }


  final val ISO_DATE_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive.append(ISO_LOCAL_DATE_TIME_3NANO)
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
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive
      .append(ISO_LOCAL_TIME_3NANO)
      .optionalStart
      .appendOffsetId
      .toFormatter
  }
}

object IzTime extends IzTime with IzTimeOrdering {

}
