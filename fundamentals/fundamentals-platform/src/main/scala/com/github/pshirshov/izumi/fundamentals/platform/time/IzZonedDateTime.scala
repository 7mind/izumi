package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime


class IzZonedDateTime(timestamp: ZonedDateTime) {

  import IzTime._

  def isoFormatUtc: String = ISO_DATE_TIME.format(timestamp.withZoneSameInstant(TZ_UTC))

  def isoFormat: String = ISO_DATE_TIME.format(timestamp)

  def isoFormatDate: String = ISO_DATE.format(timestamp)

  def isoFormatTime: String = ISO_TIME.format(timestamp)

}
