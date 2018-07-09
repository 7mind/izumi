package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.{Instant, ZonedDateTime}

import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime.TZ_UTC

class IzLongParsers(t: Long) {
  def asEpochSecondsUtc: ZonedDateTime = {
    val instant = Instant.ofEpochSecond(t)
    ZonedDateTime.ofInstant(instant, TZ_UTC)
  }

  def asEpochMillisUtc: ZonedDateTime = {
    val instant = Instant.ofEpochMilli(t)
    ZonedDateTime.ofInstant(instant, TZ_UTC)
  }

}
