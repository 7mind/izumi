package izumi.distage.testkit.spec

import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.testkit.spec.sessionplugins.SessionScannedPlugin

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

private[spec] object SessionEnvironmentFixturePlatform {
  private final val Threads = 4
  private final val Timeout = 30.seconds

  def scannedOwners(): Vector[(String, Boolean)] = {
    val config = PluginConfig(Seq("izumi.distage.testkit.spec.sessionplugins"), Nil, cachePackages = true, debug = false, Nil, Nil)
    val firstOwner = new SessionPluginLoader(new PluginLoaderDefaultImpl())
    val secondOwner = new SessionPluginLoader(new PluginLoaderDefaultImpl())
    val first = firstOwner.load(config)
    val repeated = firstOwner.load(config)
    val second = secondOwner.load(config)
    val uncachedFirst = firstOwner.load(config.cachePackages(false))
    val uncachedSecond = firstOwner.load(config.cachePackages(false))
    val all = Vector(first, repeated, second, uncachedFirst, uncachedSecond)
    require(all.forall(_.loaded.size == 1), "Scanning must find exactly the one fixture plugin")
    val plugins = all.map(_.loaded.head).collect { case plugin: SessionScannedPlugin => plugin }
    require(plugins.size == all.size, "Scanning must load the expected fixture plugin class")
    Vector(
      "JVM scanned plugins repeat inside one owner" -> ((first eq repeated) && (plugins(0) eq plugins(1))),
      "JVM independent owners scan distinct plugin objects" -> ((plugins(0) ne plugins(2)) && (plugins(0).provisions ne plugins(2).provisions)),
      "JVM uncached scans are fresh and all providers remain suspended" -> ((plugins(3) ne plugins(4)) && plugins.forall(_.provisions.get() == 0)),
    )
  }

  def concurrent(check: ExecutionContext => Future[Unit]): Unit = {
    val executor = Executors.newFixedThreadPool(Threads)
    val context = ExecutionContext.fromExecutorService(executor)
    try {
      Await.result(check(context), Timeout)
    } finally {
      executor.shutdown()
      require(executor.awaitTermination(Timeout.toMillis, TimeUnit.MILLISECONDS), "Environment fixture executor did not terminate")
    }
    println("SESSION_ENVIRONMENT_FIXTURE_EXECUTOR_TERMINATED")
  }
}
