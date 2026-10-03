package izumi.distage.testkit.runner.di

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

private[di] object ProviderFixturePlatform {
  def pluginOwnership(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    val _ = (context, verify)
    Future.successful(())
  }

  abstract class BootstrapFactoryBase extends izumi.distage.testkit.runner.impl.services.BootstrapFactory {
    override protected def makeConfigLocationProvider(name: String): izumi.distage.framework.services.ConfigLocationProvider = izumi.distage.framework.services.ConfigLocationProvider.Default
  }

  private final val Threads = 4
  private final val Timeout = 60.seconds
  private final val RegistrationTimeoutSeconds = 10L

  def concurrentDiscovery(sessions: Vector[izumi.distage.testkit.runner.RunSession], context: ExecutionContext): Future[Vector[Either[izumi.distage.testkit.protocol.Failure, izumi.distage.testkit.protocol.Catalogue]]] = {
    implicit val ec: ExecutionContext = context
    val entered = new CountDownLatch(sessions.size)
    val gate = new CountDownLatch(1)
    val discoveries = sessions.map { session => Future {
      entered.countDown()
      require(gate.await(RegistrationTimeoutSeconds, TimeUnit.SECONDS), "Concurrent registration start gate did not open")
      session.discover()
    } }
    val ready = Future {
      try require(entered.await(RegistrationTimeoutSeconds, TimeUnit.SECONDS), "Concurrent registration workers did not enter")
      finally gate.countDown()
    }
    ready.flatMap(_ => Future.sequence(discoveries))
  }

  def activationConfig(choice: izumi.distage.testkit.protocol.AxisChoice): izumi.distage.config.model.AppConfig = {
    izumi.distage.config.model.AppConfig.provided(io.circe.JsonObject("activation" -> io.circe.Json.obj(choice.axis.value -> io.circe.Json.fromString(choice.value.value))))
  }

  def run(check: ExecutionContext => Future[Unit]): Unit = {
    val executor = Executors.newFixedThreadPool(Threads)
    val context = ExecutionContext.fromExecutorService(executor)
    try Await.result(check(context), Timeout)
    finally {
      executor.shutdown()
      require(executor.awaitTermination(Timeout.toMillis, TimeUnit.MILLISECONDS), "Provider fixture executor did not terminate")
    }
    println("DISTAGE_PROVIDER_EXECUTOR_TERMINATED")
  }
}
