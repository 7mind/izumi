package izumi.distage.testkit.services.dstest

import izumi.distage.model.providers.Functoid
import izumi.distage.testkit.services.dstest.DistageTestRunner.TestId
import izumi.fundamentals.platform.language.SourceFilePosition

trait TestRegistration[F[_]] {
  def registerTest(function: Functoid[F[?]], env: TestEnvironment, pos: SourceFilePosition, id: TestId): Unit
}
