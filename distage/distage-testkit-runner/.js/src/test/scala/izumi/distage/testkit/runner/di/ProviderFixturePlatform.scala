package izumi.distage.testkit.runner.di

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.timers.setTimeout
import scala.util.{Failure, Success}

private[di] object ProviderFixturePlatform {
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
    check(context).onComplete {
      case Success(_) => println("DISTAGE_PROVIDER_JS_COMPLETED")
      case Failure(cause) => throw cause
    }(context)
  }
}
