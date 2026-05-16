package izumi.distage.testkit.distagesuite.parallel

import distage.DIKey
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.distagesuite.memoized.MemoizationEnv.MemoizedInstance
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.scalatest.Spec2
import izumi.logstage.api.Log
import zio.ZIO

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt

object DistageParallelLevelTest {
  val zioCounter = new AtomicInteger(0)
}

abstract class DistageParallelLevelTestZIO(
  suitesCounter: AtomicInteger
) extends Spec2[zio.IO] {
  private final val maxSuites = 3
  private final val maxTests = 2
  private final val testsCounter = new AtomicInteger(0)

  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = Set(DIKey.get[MemoizedInstance]),
      pluginConfig = PluginConfig.empty,
      parallelTests = Parallelism.Fixed(maxTests),
      parallelSuites = Parallelism.Fixed(maxSuites),
      parallelEnvs = Parallelism.Sequential,
      logLevel = Log.Level.Error,
    )
  }

  private def checkCounters: zio.IO[Throwable, Unit] = {
    for {
      _ <- ZIO.attempt {
        val testsCounterVal = testsCounter.addAndGet(1)
        val suitesCounterVal =
          if (testsCounterVal == 1) {
            suitesCounter.addAndGet(1)
          } else {
            suitesCounter.get()
          }
        assert(suitesCounterVal <= maxSuites && testsCounterVal <= maxTests)
      }
      _ <- ZIO.sleep(zio.Duration.fromScala(500.millis))
      _ <- ZIO.succeed {
        val newTestsCounter = testsCounter.decrementAndGet()
        if (newTestsCounter == 0) {
          suitesCounter.decrementAndGet()
        }
        ()
      }
    } yield ()
  }

  "parallel test level should be bounded by config 1" in checkCounters
  "parallel test level should be bounded by config 2" in checkCounters
  "parallel test level should be bounded by config 3" in checkCounters
  "parallel test level should be bounded by config 4" in checkCounters
}

final class DistageParallelLevelTestZIO1 extends DistageParallelLevelTestZIO(DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO2 extends DistageParallelLevelTestZIO(DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO3 extends DistageParallelLevelTestZIO(DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO4 extends DistageParallelLevelTestZIO(DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO5 extends DistageParallelLevelTestZIO(DistageParallelLevelTest.zioCounter)
final class DistageParallelLevelTestZIO6 extends DistageParallelLevelTestZIO(DistageParallelLevelTest.zioCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}
