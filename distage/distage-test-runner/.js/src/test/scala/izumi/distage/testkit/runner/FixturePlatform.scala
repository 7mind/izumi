package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{Catalogue, Failure as ProtocolFailure}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.{Failure, Success}

object FixturePlatform {
  private final val TimeoutMillis = 30000

  def concurrentDiscovery(sessions: Vector[RunSession], context: ExecutionContext): Future[Vector[Either[ProtocolFailure, Catalogue]]] = {
    implicit val ec: ExecutionContext = context
    Future.sequence(sessions.map(session => Future(session.discover())))
  }

  def run(body: ExecutionContext => Future[Unit]): Unit = {
    implicit val executionContext: ExecutionContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = { val _ = setTimeout(0)(runnable.run()); () }
      override def reportFailure(cause: Throwable): Unit = throw cause
    }
    val timeout = setTimeout(TimeoutMillis)(throw new IllegalStateException("Base runner fixture timed out"))
    body(executionContext).onComplete {
      case Success(_) => clearTimeout(timeout)
      case Failure(cause) => clearTimeout(timeout); throw cause
    }
  }
}
