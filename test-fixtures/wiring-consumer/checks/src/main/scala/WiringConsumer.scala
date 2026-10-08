package izumi.fixtures.wiring

import izumi.distage.framework.PlanCheckConfig
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{SpecWiring, TestAssertions, WiringAssertions}
import izumi.fundamentals.assertions.AssertionFailure

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService}
import scala.concurrent.duration.*

final class ConfiguredWiringSuite extends SpecWiring(ConsumerApp, PlanCheckConfig(checkConfig = false)) {
  override protected def config: TestConfig = TestConfig.empty.copy(pluginConfig = PluginConfig.empty)
}

final class DefaultWiringSuite extends SpecWiring(ConsumerApp) {
  override protected def config: TestConfig = TestConfig.empty.copy(pluginConfig = PluginConfig.empty)
}

object WiringConsumer {
  private final val Deadline = 60.seconds
  private final class Assertions extends TestAssertions with WiringAssertions

  def main(args: Array[String]): Unit = {
    val assertions = new Assertions
    assertions.assertWiringCompileTime(ConsumerApp, PlanCheckConfig(checkConfig = false))
    assertions.assertWiringCompileTime(ConsumerApp, PlanCheckConfig.empty)
    val _ = assertions.intercept[AssertionFailure] {
      assertions.assertWiringCompileTime(MissingDependencyApp, PlanCheckConfig(checkConfig = false, onlyWarn = true))
    }
    val context: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    try {
      val identity = CatalogueIdentity(BuildId("published-wiring"), BuildTargetId("jvm"), CatalogueId("real-macros"))
      val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = () }
      val session = new RunSession(identity, Vector(() => new ConfiguredWiringSuite, () => new DefaultWiringSuite), context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      require(catalogue.suites.size == 2 && catalogue.tests.size == 4, "Published wiring suites must register both checks")
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val result = Await.result(session.execute(RunId("wiring-consumer"), request), Deadline)
      require(result.successful && result.results.size == 4, "Actual materialized wiring checks must pass")
      println("PUBLISHED_WIRING_CONSUMER_OK suites=2 cases=4 assertions=3 materializer=compiler defaults=preserved")
    } finally {
      context.shutdown()
      require(context.awaitTermination(Deadline.toSeconds, TimeUnit.SECONDS), "Published wiring consumer executor must terminate")
    }
  }
}
