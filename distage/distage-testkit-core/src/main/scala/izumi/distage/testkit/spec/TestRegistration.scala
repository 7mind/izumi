package izumi.distage.testkit.spec

import izumi.distage.model.providers.Functoid
import izumi.distage.testkit.model.TestId
import izumi.distage.testkit.runner.TestEnvironment
import izumi.fundamentals.platform.language.SourceFilePosition

trait TestRegistration[F[_]] {
  def registerTest[A](function: Functoid[F[A]], env: TestEnvironment, pos: SourceFilePosition, id: TestId): Unit
}
