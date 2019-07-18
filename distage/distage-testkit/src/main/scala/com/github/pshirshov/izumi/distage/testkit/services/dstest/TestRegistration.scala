package com.github.pshirshov.izumi.distage.testkit.services.dstest

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.services.dstest.DistageTestRunner.TestId
import com.github.pshirshov.izumi.fundamentals.platform.jvm.CodePosition

trait TestRegistration[F[_]] {
  protected[testkit] def registerTest(function: ProviderMagnet[F[_]], env: TestEnvironment, pos: CodePosition, id: TestId): Unit
}
