package izumi.distage.testkit.runner.di

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

private[di] object ProviderFixturePlatform {
  private final val Threads = 4
  private final val Timeout = 60.seconds

  def run(check: ExecutionContext => Future[Unit]): Unit = {
    val executor = Executors.newFixedThreadPool(Threads)
    val context = ExecutionContext.fromExecutorService(executor)
    try Await.result(check(context), Timeout)
    finally {
      executor.shutdown()
      require(executor.awaitTermination(Timeout.toMillis, TimeUnit.MILLISECONDS), "Provider fixture executor did not terminate")
    }
    println("DISTAGE_PROVIDER_EXECUTOR_TERMINATED")
  }
}
