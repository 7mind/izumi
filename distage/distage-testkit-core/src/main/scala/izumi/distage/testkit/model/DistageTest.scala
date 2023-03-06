package izumi.distage.testkit.model

import distage.Functoid
import izumi.fundamentals.platform.language.SourceFilePosition

import java.util.UUID

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

final case class ScopeId(id: UUID) extends AnyVal