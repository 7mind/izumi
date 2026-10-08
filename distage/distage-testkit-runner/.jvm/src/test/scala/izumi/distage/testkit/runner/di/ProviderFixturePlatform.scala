package izumi.distage.testkit.runner.di

import izumi.distage.testkit.runner.ConcurrentDiscoveryFixture

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

private[di] object ProviderFixturePlatform {
  def pluginOwnership(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = SpecPluginOwnershipFixtures.run(context, verify)

  abstract class BootstrapFactoryBase extends izumi.distage.testkit.runner.impl.services.BootstrapFactory {
    override protected def makeConfigLocationProvider(name: String): izumi.distage.framework.services.ConfigLocationProvider = izumi.distage.framework.services.ConfigLocationProvider.Default
  }

  private final val Threads = 4
  private final val Timeout = 60.seconds
  private final val RegistrationTimeoutSeconds = 10L

  def concurrentDiscovery(sessions: Vector[izumi.distage.testkit.runner.RunSession], context: ExecutionContext): Future[Vector[Either[izumi.distage.testkit.protocol.Failure, izumi.distage.testkit.protocol.Catalogue]]] =
    ConcurrentDiscoveryFixture(sessions, context, RegistrationTimeoutSeconds)

  def activationConfig(choice: izumi.distage.testkit.protocol.AxisChoice): izumi.distage.config.model.AppConfig = {
    val values = java.util.Map.of("activation", java.util.Map.of(choice.axis.value, choice.value.value))
    izumi.distage.config.model.AppConfig.provided(com.typesafe.config.ConfigFactory.parseMap(values))
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
