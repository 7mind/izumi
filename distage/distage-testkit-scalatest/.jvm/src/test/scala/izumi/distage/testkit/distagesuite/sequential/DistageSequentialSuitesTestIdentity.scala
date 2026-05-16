package izumi.distage.testkit.distagesuite.sequential

import distage.DIKey
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.distagesuite.memoized.MemoizationEnv.MemoizedInstance
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.scalatest.SpecIdentity
import izumi.logstage.api.Log

import java.util.concurrent.atomic.AtomicInteger

object DistageSequentialSuitesTestIdentity {
  val idCounter = new AtomicInteger(0)
}

// JVM-only Identity tests — mirror `DistageSequentialSuitesTestZIO` running on the
// `IdentityBifunctorized` (MiniBIO-backed) carrier. `Temporal2[IdentityBifunctorized]`
// (added in M5-fix5a) provides `sleep` via `Thread.sleep`. Bodies are written as plain
// `Unit` (the SpecIdentity DSL lifts them through `Bifunctorized.bifunctorizeIdentity`).
abstract class DistageSequentialSuitesTestIdentity(
  suitesCounter: AtomicInteger
) extends SpecIdentity {
  private val maxSuites = 1
  private val maxTests = 2
  private val testsCounter = new AtomicInteger(0)

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

final class DistageSequentialSuitesTestId1 extends DistageSequentialSuitesTestIdentity(DistageSequentialSuitesTestIdentity.idCounter)
final class DistageSequentialSuitesTestId2 extends DistageSequentialSuitesTestIdentity(DistageSequentialSuitesTestIdentity.idCounter)
final class DistageSequentialSuitesTestId3 extends DistageSequentialSuitesTestIdentity(DistageSequentialSuitesTestIdentity.idCounter)
final class DistageSequentialSuitesTestId4 extends DistageSequentialSuitesTestIdentity(DistageSequentialSuitesTestIdentity.idCounter)
final class DistageSequentialSuitesTestId5 extends DistageSequentialSuitesTestIdentity(DistageSequentialSuitesTestIdentity.idCounter)
final class DistageSequentialSuitesTestId6 extends DistageSequentialSuitesTestIdentity(DistageSequentialSuitesTestIdentity.idCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}
