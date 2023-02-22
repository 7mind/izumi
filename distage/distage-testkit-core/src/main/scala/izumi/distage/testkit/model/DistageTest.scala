package izumi.distage.testkit.model

import distage.Functoid
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.SourceFilePosition

import scala.concurrent.duration.FiniteDuration

final case class DistageTest[F[_]](
  test: Functoid[F[Any]],
  environment: TestEnvironment,
  testMeta: TestMeta,
  suiteMeta: SuiteMeta,
) {
  def meta: FullMeta = FullMeta(testMeta, suiteMeta)
}

final case class TestId(path: Seq[String], suite: SuiteId) {
  lazy val name: String = path.mkString(" ")
  override def toString: String = s"${suite.suiteId}/$name"
}

final case class TestMeta(id: TestId, pos: SourceFilePosition, uid: Long)

final case class SuiteId(suiteId: String) extends AnyVal

final case class SuiteMeta(suiteId: SuiteId, suiteName: String, suiteClassName: String)

final case class FullMeta(test: TestMeta, suite: SuiteMeta)

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
