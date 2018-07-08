package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.temporal.TemporalAccessor
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZonedDateTime}

trait TimeExt[T <: TemporalAccessor] {
  def isoFormat: String

  def isoFormatTime: String

  def isoFormatDate: String

  def <=(other: T): Boolean

  def >=(other: T): Boolean

  def <(other: T): Boolean

  def >(other: T): Boolean
}

class IzZonedDateTime(timestamp: ZonedDateTime) extends TimeExt[ZonedDateTime] {

  import IzTime._

  def isoFormatUtc: String = ISO_DATE_TIME_3NANO.format(timestamp.withZoneSameInstant(TZ_UTC))

  def isoFormat: String = ISO_DATE_TIME_3NANO.format(timestamp)

  def isoFormatTime: String = ISO_TIME_3NANO.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def <=(other: ZonedDateTime): Boolean = {
    timestamp.isEqual(other) || timestamp.isBefore(other)
  }

  def >=(other: ZonedDateTime): Boolean = {
    timestamp.isEqual(other) || timestamp.isAfter(other)
  }

  def <(other: ZonedDateTime): Boolean = {
    timestamp.isBefore(other)
  }

  def >(other: ZonedDateTime): Boolean = {
    timestamp.isAfter(other)
  }

}

class IzLocalDateTime(timestamp: LocalDateTime) extends TimeExt[LocalDateTime] {

  import IzTime._

  def isoFormat: String = ISO_LOCAL_DATE_TIME_3NANO.format(timestamp)

  def isoFormatTime: String = ISO_LOCAL_TIME_3NANO.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def <=(other: LocalDateTime): Boolean = {
    timestamp.isEqual(other) || timestamp.isBefore(other)
  }

  def >=(other: LocalDateTime): Boolean = {
    timestamp.isEqual(other) || timestamp.isAfter(other)
  }

  def <(other: LocalDateTime): Boolean = {
    timestamp.isBefore(other)
  }

  def >(other: LocalDateTime): Boolean = {
    timestamp.isAfter(other)
  }

}

class IzOffsetDateTime(timestamp: OffsetDateTime) extends TimeExt[OffsetDateTime] {

  import IzTime._

  def isoFormatUtc: String = ISO_DATE_TIME_3NANO.format(timestamp.toZonedDateTime.withZoneSameInstant(TZ_UTC))

  def isoFormat: String = ISO_OFFSET_DATE_TIME_3NANO.format(timestamp)

  def isoFormatTime: String = ISO_OFFSET_TIME_3NANO.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def <=(other: OffsetDateTime): Boolean = {
    timestamp.isEqual(other) || timestamp.isBefore(other)
  }

  def >=(other: OffsetDateTime): Boolean = {
    timestamp.isEqual(other) || timestamp.isAfter(other)
  }

  def <(other: OffsetDateTime): Boolean = {
    timestamp.isBefore(other)
  }

  def >(other: OffsetDateTime): Boolean = {
    timestamp.isAfter(other)
  }

}

class IzInstant(timestamp: Instant) extends TimeExt[Instant] {

  import IzTime._

  def isoFormat: String = ISO_DATE_TIME_3NANO.format(timestamp)

  def isoFormatTime: String = ISO_TIME_3NANO.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def <=(other: Instant): Boolean = {
    timestamp.equals(other) || timestamp.isBefore(other)
  }

  def >=(other: Instant): Boolean = {
    timestamp.equals(other) || timestamp.isAfter(other)
  }

  def <(other: Instant): Boolean = {
    timestamp.isBefore(other)
  }

  def >(other: Instant): Boolean = {
    timestamp.isAfter(other)
  }

}

