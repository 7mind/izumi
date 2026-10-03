package izumi.distage.testkit.spec

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.timers.setTimeout
import scala.util.{Failure, Success}

private[spec] object SessionEnvironmentFixturePlatform {
  def runnerCompletionChecks(): Vector[(String, Boolean)] = Vector.empty

  def scannedOwners(): Vector[(String, Boolean)] = Vector.empty

  def concurrent(check: ExecutionContext => Future[Unit]): Unit = {
    val context = new ExecutionContext {
      override def execute(command: Runnable): Unit = { val _ = setTimeout(0)(command.run()) }
      override def reportFailure(cause: Throwable): Unit = throw cause
    }
    check(context).onComplete {
      case Success(_) => println("SESSION_ENVIRONMENT_FIXTURE_JS_COMPLETED")
      case Failure(cause) => throw cause
    }(context)
  }
}
