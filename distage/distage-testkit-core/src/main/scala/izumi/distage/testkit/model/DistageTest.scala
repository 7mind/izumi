package izumi.distage.testkit.model

import distage.Functoid
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.SourceFilePosition

import scala.concurrent.duration.FiniteDuration

final case class DistageTest[F[_]](
  test: Functoid[F[Any]],
  environment: TestEnvironment,
  meta: TestMeta,
)

final case class TestId(path: Seq[String], suite: SuiteMeta) {
  lazy val name: String = path.mkString(" ")
  override def toString: String = s"${suite.suiteName}: $name"
}

final case class TestMeta(id: TestId, pos: SourceFilePosition, uid: Long)

final case class SuiteId(suiteId: String) extends AnyVal

final case class SuiteMeta(suiteId: SuiteId, suiteName: String, suiteClassName: String)

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
