package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.concurrent.TimeUnit

import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime.{ISO_DATE, ISO_DATE_TIME_3NANO}

import scala.concurrent.duration.{Duration, FiniteDuration}

class IzTimeParsers(s: String) {
  def toFiniteDuration: FiniteDuration = FiniteDuration(Duration(s).toNanos, TimeUnit.NANOSECONDS)

  def toTemporal: TemporalAccessor = ISO_DATE_TIME_3NANO.parse(s)

  def toDate: TemporalAccessor = ISO_DATE.parse(s)

  def toTsZ: ZonedDateTime = ZonedDateTime.parse(s, ISO_DATE_TIME_3NANO)

}

