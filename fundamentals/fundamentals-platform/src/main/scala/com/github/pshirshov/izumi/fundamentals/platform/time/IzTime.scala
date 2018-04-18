package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time._
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField.{HOUR_OF_DAY, MINUTE_OF_HOUR, NANO_OF_SECOND, SECOND_OF_MINUTE}
import java.util.Date

import scala.language.implicitConversions


object IzTime {
  final val TZ_UTC: ZoneId = ZoneId.of("UTC")

  final val ISO_LOCAL_TIME = {
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

  final val ISO_LOCAL_DATE_TIME = {
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive
      .append(DateTimeFormatter.ISO_LOCAL_DATE)
      .appendLiteral('T')
      .append(ISO_LOCAL_TIME)
      .toFormatter()
  }

  final val ISO_DATE_TIME = {
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive.append(ISO_LOCAL_DATE_TIME)
      .appendOffsetId
      .toFormatter()
  }
  //DateTimeFormatter.ISO_OFFSET_DATE_TIME
  final val ISO_DATE = DateTimeFormatter.ISO_DATE
  final val ISO_TIME = DateTimeFormatter.ISO_TIME

  implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] = Ordering.fromLessThan(_ isBefore _)

  implicit def localDateTimeOrdering: Ordering[LocalDateTime] = Ordering.fromLessThan(_ isBefore _)

  implicit def instantDateTimeOrdering: Ordering[Instant] = Ordering.fromLessThan(_ isBefore _)

  implicit def offsetDateTimeOrdering: Ordering[OffsetDateTime] = Ordering.fromLessThan(_ isBefore _)

  implicit def toRich(value: Date): IzDate = new IzDate(value)

  implicit def toParseableTime(value: String): IzTimeParsers = new IzTimeParsers(value)

  implicit def toParseableTime(value: Option[String]): IzOptionalTimeParsers = new IzOptionalTimeParsers(value)

  implicit def toRich(timestamp: ZonedDateTime): IzZonedDateTime = new IzZonedDateTime(timestamp)

  implicit def toRich(value: Long): IzLongParsers = new IzLongParsers(value)

  def utcNow: ZonedDateTime = ZonedDateTime.now(TZ_UTC)

  final val EPOCH = ZonedDateTime.ofInstant(Instant.ofEpochSecond(1), TZ_UTC)

  def isoNow: String = utcNow.isoFormat
}
