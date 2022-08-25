package izumi.fundamentals.platform.resources

import java.time.LocalDateTime
import izumi.fundamentals.platform.time.IzTime.*

case class BuildStatus(user: String, jdk: String, sbt: String, timestamp: LocalDateTime) {
  override def toString: String = s"$user@${timestamp.isoFormat}, JDK $jdk, SBT $sbt"
}
