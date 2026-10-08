package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{Catalogue, Failure}

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.{ExecutionContext, Future}

private[runner] object ConcurrentDiscoveryFixture {
  def apply(sessions: Vector[RunSession], context: ExecutionContext, timeoutSeconds: Long): Future[Vector[Either[Failure, Catalogue]]] = {
    implicit val ec: ExecutionContext = context
    val entered = new CountDownLatch(sessions.size)
    val gate = new CountDownLatch(1)
    val discoveries = sessions.map { session => Future {
      entered.countDown()
      require(gate.await(timeoutSeconds, TimeUnit.SECONDS), "Concurrent registration start gate did not open")
      session.discover()
    } }
    val ready = Future {
      try require(entered.await(timeoutSeconds, TimeUnit.SECONDS), "Concurrent registration workers did not enter")
      finally gate.countDown()
    }
    ready.flatMap(_ => Future.sequence(discoveries))
  }
}
