package izumi.distage.testkit.runner.di

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.timers.setTimeout
import scala.util.{Failure, Success}

private[di] object ProviderFixturePlatform {
  def run(check: ExecutionContext => Future[Unit]): Unit = {
    val context = new ExecutionContext {
      override def execute(command: Runnable): Unit = { val _ = setTimeout(0)(command.run()) }
      override def reportFailure(cause: Throwable): Unit = throw cause
    }
    check(context).onComplete {
      case Success(_) => println("DISTAGE_PROVIDER_JS_COMPLETED")
      case Failure(cause) => throw cause
    }(context)
  }
}
