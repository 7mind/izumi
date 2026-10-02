package izumi.distage.testkit.runner

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

object FixturePlatform {
  private final val WorkerThreads = 4
  private final val TimeoutSeconds = 30

  def run(body: ExecutionContext => Future[Unit]): Unit = {
    val executor = Executors.newFixedThreadPool(WorkerThreads)
    val executionContext = ExecutionContext.fromExecutorService(executor)
    try Await.result(body(executionContext), TimeoutSeconds.seconds)
    finally {
      executionContext.shutdown()
      if (!executionContext.awaitTermination(TimeoutSeconds, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture execution context did not terminate")
    }
    bootstrap.BootstrapFixtures.main(Array.empty)
  }
}
