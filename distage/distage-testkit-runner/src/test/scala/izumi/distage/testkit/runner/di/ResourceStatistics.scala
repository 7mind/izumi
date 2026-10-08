package izumi.distage.testkit.runner.di

import java.util.concurrent.atomic.AtomicInteger

private[di] abstract class ResourceStatistics {
  val acquired = new AtomicInteger(0)
  val released = new AtomicInteger(0)
  val bodies = new AtomicInteger(0)
}
