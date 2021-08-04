package izumi.functional.bio

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

private[bio] object __VersionSpecificDurationConvertersCompat {

  /**
   * Transform a Java duration into a Scala duration. If the nanosecond part of the Java duration is zero the returned
   * duration will have a time unit of seconds and if there is a nanoseconds part the Scala duration will have a time
   * unit of nanoseconds.
   *
   * @throws IllegalArgumentException If the given Java Duration is out of bounds of what can be expressed with the
   *                                  Scala FiniteDuration.
   */
  private[bio] final def toFiniteDuration(duration: java.time.Duration): FiniteDuration = {
    val originalSeconds = duration.getSeconds
    val originalNanos = duration.getNano
    if (originalNanos == 0) {
      if (originalSeconds == 0) Duration.Zero
      else FiniteDuration(originalSeconds, TimeUnit.SECONDS)
    } else if (originalSeconds == 0) {
      FiniteDuration(originalNanos.toLong, TimeUnit.NANOSECONDS)
    } else {
      try {
        val secondsAsNanos = Math.multiplyExact(originalSeconds, 1000000000)
        val totalNanos = secondsAsNanos + originalNanos
        if ((totalNanos < 0 && secondsAsNanos < 0) || (totalNanos > 0 && secondsAsNanos > 0)) FiniteDuration(totalNanos, TimeUnit.NANOSECONDS)
        else throw new ArithmeticException()
      } catch {
        case _: ArithmeticException => throw new IllegalArgumentException(s"Java duration $duration cannot be expressed as a Scala duration")
      }
    }
  }

}
