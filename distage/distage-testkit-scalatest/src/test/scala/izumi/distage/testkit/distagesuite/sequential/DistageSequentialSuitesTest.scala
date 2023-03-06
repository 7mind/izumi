package izumi.distage.testkit.distagesuite.sequential

import java.util.concurrent.atomic.AtomicInteger
import cats.effect.IO as CIO
import distage.{DIKey, TagK}
import izumi.functional.quasi.QuasiIO.syntax.QuasiIOSyntax
import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.distagesuite.memoized.MemoizationEnv.MemoizedInstance
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.Spec1
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.Log
import zio.Task

import scala.concurrent.duration.DurationInt

object DistageSequentialSuitesTest {
  val idCounter = new AtomicInteger(0)
  val cioCounter = new AtomicInteger(0)
  val zioCounter = new AtomicInteger(0)
  val monixCounter = new AtomicInteger(0)
}

sealed abstract class DistageSequentialSuitesTest[F[_]: TagK: DefaultModule](
  suitesCounter: AtomicInteger
)(implicit F: QuasiIO[F]
) extends Spec1[F] {
  private[this] val maxSuites = 1
  private[this] val maxTests = 2
  private[this] val testsCounter = new AtomicInteger(0)

  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = Set(DIKey.get[MemoizedInstance]),
      pluginConfig = PluginConfig.empty,
      parallelTests = Parallelism.Fixed(maxTests),
      parallelSuites = Parallelism.Sequential,
      parallelEnvs = Parallelism.Sequential,
      logLevel = Log.Level.Error,
    )
  }

  private[this] def checkCounters: QuasiAsync[F] => F[Unit] = {
    FA =>
      F.suspendF {
        val testsCounterVal = testsCounter.addAndGet(1)
        val suitesCounterVal =
          if (testsCounterVal == 1) {
            suitesCounter.addAndGet(1)
          } else {
            suitesCounter.get()
          }

        assert(suitesCounterVal <= maxSuites && testsCounterVal <= maxTests)

        FA.sleep(500.millis).flatMap {
          _ =>
            F.maybeSuspend {
              val newTestsCounter = testsCounter.decrementAndGet()
              if (newTestsCounter == 0) {
                suitesCounter.decrementAndGet()
              }
              ()
            }
        }
      }
  }

  "parallel test level should be bounded by config 1" in checkCounters
  "parallel test level should be bounded by config 2" in checkCounters
  "parallel test level should be bounded by config 3" in checkCounters
  "parallel test level should be bounded by config 4" in checkCounters
}

final class DistageSequentialSuitesTestId1 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId2 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId3 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId4 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId5 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId6 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}

final class DistageSequentialSuitesTestCIO1 extends DistageSequentialSuitesTest[CIO](DistageSequentialSuitesTest.cioCounter)
final class DistageSequentialSuitesTestCIO2 extends DistageSequentialSuitesTest[CIO](DistageSequentialSuitesTest.cioCounter)
final class DistageSequentialSuitesTestCIO3 extends DistageSequentialSuitesTest[CIO](DistageSequentialSuitesTest.cioCounter)
final class DistageSequentialSuitesTestCIO4 extends DistageSequentialSuitesTest[CIO](DistageSequentialSuitesTest.cioCounter)
final class DistageSequentialSuitesTestCIO5 extends DistageSequentialSuitesTest[CIO](DistageSequentialSuitesTest.cioCounter)
final class DistageSequentialSuitesTestCIO6 extends DistageSequentialSuitesTest[CIO](DistageSequentialSuitesTest.cioCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}

final class DistageSequentialSuitesTestZIO1 extends DistageSequentialSuitesTest[Task](DistageSequentialSuitesTest.zioCounter)
final class DistageSequentialSuitesTestZIO2 extends DistageSequentialSuitesTest[Task](DistageSequentialSuitesTest.zioCounter)
final class DistageSequentialSuitesTestZIO3 extends DistageSequentialSuitesTest[Task](DistageSequentialSuitesTest.zioCounter)
final class DistageSequentialSuitesTestZIO4 extends DistageSequentialSuitesTest[Task](DistageSequentialSuitesTest.zioCounter)
final class DistageSequentialSuitesTestZIO5 extends DistageSequentialSuitesTest[Task](DistageSequentialSuitesTest.zioCounter)
final class DistageSequentialSuitesTestZIO6 extends DistageSequentialSuitesTest[Task](DistageSequentialSuitesTest.zioCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}

//final class DistageSequentialSuitesTestMonixBIO1 extends DistageSequentialSuitesTest[monix.bio.Task](DistageSequentialSuitesTest.monixCounter)
//final class DistageSequentialSuitesTestMonixBIO2 extends DistageSequentialSuitesTest[monix.bio.Task](DistageSequentialSuitesTest.monixCounter)
//final class DistageSequentialSuitesTestMonixBIO3 extends DistageSequentialSuitesTest[monix.bio.Task](DistageSequentialSuitesTest.monixCounter)
//final class DistageSequentialSuitesTestMonixBIO4 extends DistageSequentialSuitesTest[monix.bio.Task](DistageSequentialSuitesTest.monixCounter)
//final class DistageSequentialSuitesTestMonixBIO5 extends DistageSequentialSuitesTest[monix.bio.Task](DistageSequentialSuitesTest.monixCounter)
//final class DistageSequentialSuitesTestMonixBIO6 extends DistageSequentialSuitesTest[monix.bio.Task](DistageSequentialSuitesTest.monixCounter) {
//  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
//}
