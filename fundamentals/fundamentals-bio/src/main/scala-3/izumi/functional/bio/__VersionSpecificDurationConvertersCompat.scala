package izumi.functional.bio

import scala.jdk.DurationConverters.JavaDurationOps

private[bio] object __VersionSpecificDurationConvertersCompat {

  @inline private[bio] final def toFiniteDuration(duration: java.time.Duration): scala.concurrent.duration.FiniteDuration = {
    duration.toScala
  }

}
