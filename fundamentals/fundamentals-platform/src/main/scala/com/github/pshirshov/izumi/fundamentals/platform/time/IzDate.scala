package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime
import java.util.Date

import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime.TZ_UTC

class IzDate(value: Date) {
  def toTsAsUtc: ZonedDateTime = value.toInstant.atZone(TZ_UTC)

}
