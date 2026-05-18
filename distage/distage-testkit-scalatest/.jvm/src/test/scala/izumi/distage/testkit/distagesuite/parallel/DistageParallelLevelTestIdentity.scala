package izumi.distage.testkit.distagesuite.parallel

import distage.DIKey
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.distagesuite.memoized.MemoizationEnv.MemoizedInstance
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.scalatest.SpecIdentity
import izumi.logstage.api.Log

import java.util.concurrent.atomic.AtomicInteger

object DistageParallelLevelTestIdentity {
  val idCounter = new AtomicInteger(0)
}

// JVM-only Identity tests — mirror `DistageParallelLevelTestZIO` running on the
// `IdentityBifunctorized` (MiniBIO-backed) carrier. `Temporal2[IdentityBifunctorized]`
// (added in M5-fix5a) provides `sleep` via `Thread.sleep`. Bodies are written as plain
// `Unit` (the SpecIdentity DSL lifts them through `Bifunctorized.bifunctorizeIdentity`).
abstract class DistageParallelLevelTestIdentity(
  suitesCounter: AtomicInteger
) extends SpecIdentity {
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

  private def checkCounters: Unit = {
    val testsCounterVal = testsCounter.addAndGet(1)
    val suitesCounterVal =
      if (testsCounterVal == 1) {
        suitesCounter.addAndGet(1)
      } else {
        suitesCounter.get()
      }
    assert(suitesCounterVal <= maxSuites && testsCounterVal <= maxTests)
    Thread.sleep(500)
    val newTestsCounter = testsCounter.decrementAndGet()
    if (newTestsCounter == 0) {
      suitesCounter.decrementAndGet()
    }
    ()
  }

  "parallel test level should be bounded by config 1" in checkCounters
  "parallel test level should be bounded by config 2" in checkCounters
  "parallel test level should be bounded by config 3" in checkCounters
  "parallel test level should be bounded by config 4" in checkCounters
}

final class DistageParallelLevelTestId1 extends DistageParallelLevelTestIdentity(DistageParallelLevelTestIdentity.idCounter)
final class DistageParallelLevelTestId2 extends DistageParallelLevelTestIdentity(DistageParallelLevelTestIdentity.idCounter)
final class DistageParallelLevelTestId3 extends DistageParallelLevelTestIdentity(DistageParallelLevelTestIdentity.idCounter)
final class DistageParallelLevelTestId4 extends DistageParallelLevelTestIdentity(DistageParallelLevelTestIdentity.idCounter)
final class DistageParallelLevelTestId5 extends DistageParallelLevelTestIdentity(DistageParallelLevelTestIdentity.idCounter)
final class DistageParallelLevelTestId6 extends DistageParallelLevelTestIdentity(DistageParallelLevelTestIdentity.idCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}
