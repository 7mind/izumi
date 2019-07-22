package com.github.pshirshov.izumi.logstage.api.rendering

import java.time.{Instant, OffsetDateTime, ZoneId}

object TimeOps {
  def convertToLocalTime(millis: Long): OffsetDateTime = {
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toOffsetDateTime
  }
}
