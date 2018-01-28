package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor

class IzOptionalTimeParsers(value: Option[String]) {
  import IzTime._
  def mapToTemporal: Option[TemporalAccessor] = value.map(_.toTemporal)

  def mapToTsZ: Option[ZonedDateTime] = value.map(_.toTsZ)
}
