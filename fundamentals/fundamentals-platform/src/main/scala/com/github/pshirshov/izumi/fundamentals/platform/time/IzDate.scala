package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.OffsetDateTime
import java.util.Date

import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime.TZ_UTC

class IzDate(value: Date) {
  def toTsAsUtc: OffsetDateTime = value.toInstant.atZone(TZ_UTC).toOffsetDateTime

  def <=(other: Date): Boolean = {
    value.equals(other) || value.before(other)
  }

  def >=(other: Date): Boolean = {
    value.equals(other) || value.before(other)
  }

  def <(other: Date): Boolean = {
    value.before(other)
  }

  def >(other: Date): Boolean = {
    value.after(other)
  }
}
