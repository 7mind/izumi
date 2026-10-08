package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{Catalogue, Failure}

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

object FixturePlatform {
  private final val WorkerThreads = 4
  private final val TimeoutSeconds = 30

  def concurrentDiscovery(sessions: Vector[RunSession], context: ExecutionContext): Future[Vector[Either[Failure, Catalogue]]] =
    ConcurrentDiscoveryFixture(sessions, context, TimeoutSeconds)

  def run(body: ExecutionContext => Future[Unit]): Unit = {
    val executor = Executors.newFixedThreadPool(WorkerThreads)
    val executionContext = ExecutionContext.fromExecutorService(executor)
    try Await.result(body(executionContext).flatMap(_ => FileProtocolFrameFixtures.run(executionContext))(using executionContext), TimeoutSeconds.seconds)
    finally {
      executionContext.shutdown()
      if (!executionContext.awaitTermination(TimeoutSeconds, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture execution context did not terminate")
    }
    ApplicationBlockingFixtures.main(Array.empty)
    ApplicationOutputBlockingFixtures.main(Array.empty)
    bootstrap.BootstrapFixtures.main(Array.empty)
    bootstrap.RegistrationLinkageFixtures.main(Array.empty)
    bootstrap.WorkerClassLoaderFixtures.main(Array.empty)
  }
}
