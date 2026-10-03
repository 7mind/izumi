package izumi.distage.testkit.runner.di

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.{Failure, Success}

private[di] object ProviderFixturePlatform {
  private final val CompletionTimeout = 30.seconds

  def pluginOwnership(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    val _ = (context, verify)
    Future.successful(())
  }

  abstract class BootstrapFactoryBase extends izumi.distage.testkit.runner.impl.services.BootstrapFactory

  def concurrentDiscovery(sessions: Vector[izumi.distage.testkit.runner.RunSession], context: ExecutionContext): Future[Vector[Either[izumi.distage.testkit.protocol.Failure, izumi.distage.testkit.protocol.Catalogue]]] = {
    implicit val ec: ExecutionContext = context
    Future.sequence(sessions.map(session => Future(session.discover())))
  }

  def activationConfig(choice: izumi.distage.testkit.protocol.AxisChoice): izumi.distage.config.model.AppConfig =
    izumi.distage.config.model.AppConfig.provided(io.circe.JsonObject("activation" -> io.circe.Json.obj(choice.axis.value -> io.circe.Json.fromString(choice.value.value))))

  def run(check: ExecutionContext => Future[Unit]): Unit = {
    val context = new ExecutionContext {
      override def execute(command: Runnable): Unit = { val _ = setTimeout(0)(command.run()) }
      override def reportFailure(cause: Throwable): Unit = throw cause
    }
    val timeout = setTimeout(CompletionTimeout) {
      throw new AssertionError("Distage provider JS fixture did not complete")
    }
    try check(context).onComplete {
      case Success(_) => clearTimeout(timeout); println("DISTAGE_PROVIDER_JS_COMPLETED")
      case Failure(cause) => clearTimeout(timeout); throw cause
    }(context)
    catch { case cause: Throwable => clearTimeout(timeout); throw cause }
  }
}
