package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{Catalogue, Failure}

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

object FixturePlatform {
  private final val WorkerThreads = 4
  private final val TimeoutSeconds = 30

  def concurrentDiscovery(sessions: Vector[RunSession], context: ExecutionContext): Future[Vector[Either[Failure, Catalogue]]] = {
    implicit val ec: ExecutionContext = context
    val entered = new CountDownLatch(sessions.size)
    val gate = new CountDownLatch(1)
    val discoveries = sessions.map { session => Future {
      entered.countDown()
      require(gate.await(TimeoutSeconds, TimeUnit.SECONDS), "Concurrent registration start gate did not open")
      session.discover()
    } }
    val ready = Future {
      try require(entered.await(TimeoutSeconds, TimeUnit.SECONDS), "Concurrent registration workers did not enter")
      finally gate.countDown()
    }
    ready.flatMap(_ => Future.sequence(discoveries))
  }

  def run(body: ExecutionContext => Future[Unit]): Unit = {
    val executor = Executors.newFixedThreadPool(WorkerThreads)
    val executionContext = ExecutionContext.fromExecutorService(executor)
    try Await.result(body(executionContext), TimeoutSeconds.seconds)
    finally {
      executionContext.shutdown()
      if (!executionContext.awaitTermination(TimeoutSeconds, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture execution context did not terminate")
    }
    ApplicationBlockingFixtures.main(Array.empty)
    ApplicationOutputBlockingFixtures.main(Array.empty)
  }
}
