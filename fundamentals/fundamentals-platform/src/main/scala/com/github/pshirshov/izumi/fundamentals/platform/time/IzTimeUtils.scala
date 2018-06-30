package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.{Instant, LocalDateTime, OffsetDateTime, ZonedDateTime}


class IzZonedDateTime(timestamp: ZonedDateTime) {

  import IzTime._

  def isoFormatUtc: String = ISO_DATE_TIME.format(timestamp.withZoneSameInstant(TZ_UTC))

  def isoFormat: String = ISO_DATE_TIME.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def isoFormatTime: String = ISO_TIME.format(timestamp)

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

class IzLocalDateTime(timestamp: LocalDateTime) {

  import IzTime._

  def isoFormat: String = ISO_DATE_TIME.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def isoFormatTime: String = ISO_TIME.format(timestamp)

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

class IzOffsetDateTime(timestamp: OffsetDateTime) {

  import IzTime._

  def isoFormat: String = ISO_DATE_TIME.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def isoFormatTime: String = ISO_TIME.format(timestamp)

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

class IzInstant(timestamp: Instant) {

  import IzTime._

  def isoFormat: String = ISO_DATE_TIME.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def isoFormatTime: String = ISO_TIME.format(timestamp)

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

