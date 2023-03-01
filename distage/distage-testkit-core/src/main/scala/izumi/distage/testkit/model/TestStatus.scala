package izumi.distage.testkit.model

import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck

import scala.concurrent.duration.FiniteDuration

sealed trait TestStatus

object TestStatus {
  case object Instantiating extends TestStatus

  case object Running extends TestStatus

  sealed trait Done extends TestStatus

  final case class Ignored(checks: NonEmptyList[ResourceCheck.Failure]) extends Done

  sealed trait Finished extends Done

  final case class Cancelled(clue: String, duration: FiniteDuration) extends Finished

  final case class Succeed(duration: FiniteDuration) extends Finished

  final case class Failed(t: Throwable, duration: FiniteDuration) extends Finished
}
