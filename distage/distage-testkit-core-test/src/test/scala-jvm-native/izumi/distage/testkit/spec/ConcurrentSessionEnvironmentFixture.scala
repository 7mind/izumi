package izumi.distage.testkit.spec

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.control.NonFatal

private[spec] trait ConcurrentSessionEnvironmentFixture {
  private final val Threads = 4
  protected final val Timeout = 30.seconds

  def runnerCompletionChecks(): Vector[(String, Boolean)] = RunnerCompletionFixtures.checks()

  def runFuture(check: ExecutionContext => Future[Unit], completionContext: ExecutionContext): Future[Unit] = {
    val executor = Executors.newFixedThreadPool(Threads)
    val context = ExecutionContext.fromExecutorService(executor)
    val result = try check(context) catch { case NonFatal(cause) => Future.failed(cause) }
    result.transform { outcome =>
      executor.shutdown()
      require(executor.awaitTermination(Timeout.toMillis, TimeUnit.MILLISECONDS), "Environment fixture executor did not terminate")
      println("SESSION_ENVIRONMENT_FIXTURE_EXECUTOR_TERMINATED")
      outcome
    }(completionContext)
  }

  def concurrent(check: ExecutionContext => Future[Unit]): Unit = {
    val executor = Executors.newFixedThreadPool(Threads)
    val context = ExecutionContext.fromExecutorService(executor)
    try {
      Await.result(check(context), Timeout)
    } finally {
      executor.shutdown()
      require(executor.awaitTermination(Timeout.toMillis, TimeUnit.MILLISECONDS), "Environment fixture executor did not terminate")
    }
    println("SESSION_ENVIRONMENT_FIXTURE_EXECUTOR_TERMINATED")
  }
}
