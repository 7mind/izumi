package izumi.distage.testkit.services.dstest

import izumi.distage.model.providers.Functoid
import izumi.distage.testkit.services.dstest.model.TestId
import izumi.fundamentals.platform.language.SourceFilePosition

trait TestRegistration[F[_]] {
  def registerTest[A](function: Functoid[F[A]], env: TestEnvironment, pos: SourceFilePosition, id: TestId): Unit
}
