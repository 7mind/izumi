package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

sealed trait ClockAccuracy

object ClockAccuracy {

  case object DEFAULT extends ClockAccuracy
  case object NANO extends ClockAccuracy
  case object MILLIS extends ClockAccuracy
  case object MICROS extends ClockAccuracy
  case object SECONDS extends ClockAccuracy
  case object MINUTES extends ClockAccuracy
  case object HOURS extends ClockAccuracy

  def applyAccuracy(now: ZonedDateTime, clockAccuracy: ClockAccuracy): ZonedDateTime = {
    clockAccuracy match {
      case ClockAccuracy.DEFAULT => now
      case ClockAccuracy.NANO => now.truncatedTo(ChronoUnit.NANOS)
      case ClockAccuracy.MILLIS => now.truncatedTo(ChronoUnit.MILLIS)
      case ClockAccuracy.MICROS => now.truncatedTo(ChronoUnit.MICROS)
      case ClockAccuracy.SECONDS => now.truncatedTo(ChronoUnit.SECONDS)
      case ClockAccuracy.MINUTES => now.truncatedTo(ChronoUnit.MINUTES)
      case ClockAccuracy.HOURS => now.truncatedTo(ChronoUnit.HOURS)
    }
  }
}
