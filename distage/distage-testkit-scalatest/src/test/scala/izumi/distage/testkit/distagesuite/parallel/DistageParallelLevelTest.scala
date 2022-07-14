package izumi.distage.testkit.distagesuite.parallel

import java.util.concurrent.atomic.AtomicInteger
import cats.effect.IO as CIO
import distage.{DIKey, TagK}
import izumi.distage.model.effect.{QuasiAsync, QuasiIO}
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.TestConfig.ParallelLevel
import izumi.distage.testkit.distagesuite.memoized.MemoizationEnv.MemoizedInstance
import izumi.distage.testkit.scalatest.Spec1
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.Log
import zio.Task

import scala.concurrent.duration.DurationInt

object DistageParallelLevelTest {
  val idCounter = new AtomicInteger(0)
  val cioCounter = new AtomicInteger(0)
  val zioCounter = new AtomicInteger(0)
  val monixCounter = new AtomicInteger(0)
}

sealed abstract class DistageParallelLevelTest[F[_]: TagK: DefaultModule](
  suitesCounter: AtomicInteger
)(implicit F: QuasiIO[F]
) extends Spec1[F] {
  private[this] final val maxSuites = 3
  private[this] final val maxTests = 2
  private[this] final val testsCounter = new AtomicInteger(0)

  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = Set(DIKey.get[MemoizedInstance]),
      pluginConfig = PluginConfig.empty,
      parallelTests = ParallelLevel.Fixed(maxTests),
      parallelSuites = ParallelLevel.Fixed(maxSuites),
      parallelEnvs = ParallelLevel.Sequential,
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

final class DistageParallelLevelTestId1 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId2 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId3 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId4 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId5 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId6 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}

final class DistageParallelLevelTestCIO1 extends DistageParallelLevelTest[CIO](DistageParallelLevelTest.cioCounter)
final class DistageParallelLevelTestCIO2 extends DistageParallelLevelTest[CIO](DistageParallelLevelTest.cioCounter)
final class DistageParallelLevelTestCIO3 extends DistageParallelLevelTest[CIO](DistageParallelLevelTest.cioCounter)
final class DistageParallelLevelTestCIO4 extends DistageParallelLevelTest[CIO](DistageParallelLevelTest.cioCounter)
final class DistageParallelLevelTestCIO5 extends DistageParallelLevelTest[CIO](DistageParallelLevelTest.cioCounter)
final class DistageParallelLevelTestCIO6 extends DistageParallelLevelTest[CIO](DistageParallelLevelTest.cioCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}

final class DistageParallelLevelTestZIO1 extends DistageParallelLevelTest[Task](DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO2 extends DistageParallelLevelTest[Task](DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO3 extends DistageParallelLevelTest[Task](DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO4 extends DistageParallelLevelTest[Task](DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO5 extends DistageParallelLevelTest[Task](DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO6 extends DistageParallelLevelTest[Task](DistageParallelLevelTest.zioCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}

//final class DistageParallelLevelTestMonixBIO1 extends DistageParallelLevelTest[monix.bio.Task](DistageParallelLevelTest.monixCounter)
//final class DistageParallelLevelTestMonixBIO2 extends DistageParallelLevelTest[monix.bio.Task](DistageParallelLevelTest.monixCounter)
//final class DistageParallelLevelTestMonixBIO3 extends DistageParallelLevelTest[monix.bio.Task](DistageParallelLevelTest.monixCounter)
//final class DistageParallelLevelTestMonixBIO4 extends DistageParallelLevelTest[monix.bio.Task](DistageParallelLevelTest.monixCounter)
//final class DistageParallelLevelTestMonixBIO5 extends DistageParallelLevelTest[monix.bio.Task](DistageParallelLevelTest.monixCounter)
//final class DistageParallelLevelTestMonixBIO6 extends DistageParallelLevelTest[monix.bio.Task](DistageParallelLevelTest.monixCounter) {
//  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
//}
