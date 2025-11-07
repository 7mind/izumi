package izumi.distage.testkit.spec

import izumi.distage.model.providers.Functoid
import izumi.distage.testkit.model.{DistageTest, SuiteMeta, TestEnvironment, TestId}
import izumi.fundamentals.platform.language.SourceFilePosition

trait TestRegistration[F[_]] {
  def registerTest[A](function: Functoid[F[A]], env: TestEnvironment, pos: SourceFilePosition, id: TestId, meta: SuiteMeta): Unit
  def registeredTests(): List[DistageTest[F]]
}
