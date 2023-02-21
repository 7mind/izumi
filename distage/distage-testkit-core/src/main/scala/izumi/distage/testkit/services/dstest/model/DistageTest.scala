package izumi.distage.testkit.services.dstest.model

import distage.Functoid
import izumi.distage.testkit.TestConfig.ParallelLevel
import izumi.distage.testkit.services.dstest.TestEnvironment
import izumi.fundamentals.platform.language.SourceFilePosition

final case class DistageTest[F[_]](test: Functoid[F[Any]], environment: TestEnvironment, meta: TestMeta)

final case class TestId(name: String, suiteName: String, suiteId: String, suiteClassName: String) {
  override def toString: String = s"$suiteName: $name"
}

final case class TestMeta(id: TestId, pos: SourceFilePosition, uid: Long)

final case class SuiteData(suiteName: String, suiteId: String, suiteClassName: String, parallelLevel: ParallelLevel)
