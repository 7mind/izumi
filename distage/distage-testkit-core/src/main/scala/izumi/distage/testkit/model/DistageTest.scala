package izumi.distage.testkit.model

import distage.Functoid
import TestConfig.ParallelLevel
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.SourceFilePosition

import scala.concurrent.duration.FiniteDuration

final case class DistageTest[F[_]](test: Functoid[F[Any]], environment: TestEnvironment, meta: TestMeta)

final case class TestId(path: Seq[String], suiteName: String, suiteId: String, suiteClassName: String) {
  lazy val name: String = path.mkString(" ")
  override def toString: String = s"$suiteName: $name"
}

final case class TestMeta(id: TestId, pos: SourceFilePosition, uid: Long)

final case class SuiteData(suiteName: String, suiteId: String, suiteClassName: String, parallelLevel: ParallelLevel)

sealed trait TestStatus

object TestStatus {
  case object Running extends TestStatus

  sealed trait Done extends TestStatus

  final case class Ignored(checks: NonEmptyList[ResourceCheck.Failure]) extends Done

  sealed trait Finished extends Done

  final case class Cancelled(clue: String, duration: FiniteDuration) extends Finished

  final case class Succeed(duration: FiniteDuration) extends Finished

  final case class Failed(t: Throwable, duration: FiniteDuration) extends Finished
}
