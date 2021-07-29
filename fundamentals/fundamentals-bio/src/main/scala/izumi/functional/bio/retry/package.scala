package izumi.functional.bio

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.{Duration, FiniteDuration}

package object retry {
  type Scheduler3[F[-_, +_, +_]] = Scheduler2[F[Any, +_, +_]]
  object Scheduler3 {
    @inline def apply[F[-_, +_, +_]: Scheduler3]: Scheduler3[F] = implicitly
  }

  @inline def toZonedDateTime(epochMillis: Long): ZonedDateTime = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
  }

  @inline def toFiniteDuration(javaDuration: java.time.Duration): FiniteDuration = {
    Duration.fromNanos(javaDuration.toNanos)
  }

  @inline def isZero(duration: FiniteDuration) = duration.toNanos == 0
}
