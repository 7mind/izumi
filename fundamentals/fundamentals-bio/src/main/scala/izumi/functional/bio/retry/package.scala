package izumi.functional.bio

import java.time.{Instant, ZoneOffset, ZonedDateTime}

package object retry {
  @inline private[bio] def toZonedDateTime(epochMillis: Long): ZonedDateTime = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
  }
}
