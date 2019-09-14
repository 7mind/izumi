package izumi.distage.testkit.services.dstest

import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.services.dstest.DistageTestRunner.TestId
import izumi.fundamentals.platform.jvm.CodePosition

trait TestRegistration[F[_]] {
  def registerTest(function: ProviderMagnet[F[_]], env: TestEnvironment, pos: CodePosition, id: TestId): Unit
}
