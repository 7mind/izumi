package izumi.fundamentals.platform.resources

import izumi.fundamentals.platform.time.IzTime.*

import java.time.LocalDateTime

case class BuildStatus(user: String, jdk: String, sbt: String, timestamp: LocalDateTime) {
  override def toString: String = s"$user@${timestamp.isoFormat}, JDK $jdk, SBT $sbt"
}
