package izumi.distage.testkit.distagesuite.sequential

import cats.effect.IO as CIO
import distage.TagK
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.scalatest.Spec1
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.Task

sealed abstract class DistageSequentialTestOrderingTestBase[F[_]: TagK: DefaultModule] extends Spec1[F] {

  override protected def config: TestConfig = {
    super.config.copy(
      pluginConfig = PluginConfig.empty,
      parallelTests = Parallelism.Sequential,
      parallelSuites = Parallelism.Sequential,
      parallelEnvs = Parallelism.Sequential,
    )
  }

  private var counter: Int = 0

  private def testCounter(expected: Int): QuasiIO[F] => F[Unit] = {
    implicit F =>
      F.maybeSuspend {
        counter += 1
        assert(counter == expected).discard()
      }
  }

  "sequential tests" should {
    "execute in declaration order 1" in testCounter(1)
    "execute in declaration order 2" in testCounter(2)
    "execute in declaration order 3" in testCounter(3)
    "execute in declaration order 4" in testCounter(4)
    "execute in declaration order 5" in testCounter(5)
    "execute in declaration order 6" in testCounter(6)
    "execute in declaration order 7" in testCounter(7)
    "execute in declaration order 8" in testCounter(8)
    "execute in declaration order 9" in testCounter(9)
    "execute in declaration order 10" in testCounter(10)
    "execute in declaration order 11" in testCounter(11)
    "execute in declaration order 12" in testCounter(12)
    "execute in declaration order 13" in testCounter(13)
    "execute in declaration order 14" in testCounter(14)
    "execute in declaration order 15" in testCounter(15)
  }
}

final class DistageSequentialTestOrderingTestId extends DistageSequentialTestOrderingTestBase[Identity]
final class DistageSequentialTestOrderingTestCIO extends DistageSequentialTestOrderingTestBase[CIO]
final class DistageSequentialTestOrderingTestZIO extends DistageSequentialTestOrderingTestBase[Task]
