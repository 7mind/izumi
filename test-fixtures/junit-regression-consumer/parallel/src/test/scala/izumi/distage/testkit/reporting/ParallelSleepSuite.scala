package izumi.distage.testkit.reporting

import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.runner.spec.SpecIdentity

import java.util.concurrent.{CountDownLatch, TimeUnit}

final class ParallelSleepSuite extends SpecIdentity {
  private val sleepMillis = 2000L
  private val startTimeoutSeconds = 10L
  private val started = new CountDownLatch(4)

  override protected def config: TestConfig = TestConfig.empty

  "intra-suite parallel sleeps" should {
    (1 to 4).foreach { index =>
      s"parallel sleep test $index" in {
        started.countDown()
        assert(started.await(startTimeoutSeconds, TimeUnit.SECONDS), "All four test bodies must start in parallel")
        Thread.sleep(sleepMillis)
        ()
      }
    }
  }
}
