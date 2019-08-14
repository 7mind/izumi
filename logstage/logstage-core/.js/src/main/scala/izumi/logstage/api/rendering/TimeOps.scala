package izumi.logstage.api.rendering

import java.time.{Instant, OffsetDateTime, ZoneOffset}

object TimeOps {
  def convertToLocalTime(millis: Long): OffsetDateTime = {
    Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC)
  }
}
