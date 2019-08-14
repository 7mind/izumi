package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}

import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime.TZ_UTC

final class IzLongParsers(private val t: Long) extends AnyVal {
  def asEpochSecondsLocal: LocalDateTime = {
    val instant = Instant.ofEpochSecond(t)
    LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
  }

  def asEpochMillisLocal: LocalDateTime = {
    val instant = Instant.ofEpochMilli(t)
    LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
  }

  def asEpochSecondsUtc: ZonedDateTime = {
    val instant = Instant.ofEpochSecond(t)
    ZonedDateTime.ofInstant(instant, TZ_UTC)
  }

  def asEpochMillisUtc: ZonedDateTime = {
    val instant = Instant.ofEpochMilli(t)
    ZonedDateTime.ofInstant(instant, TZ_UTC)
  }

}
