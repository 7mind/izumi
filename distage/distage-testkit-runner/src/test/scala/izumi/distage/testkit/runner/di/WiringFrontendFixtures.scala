package izumi.distage.testkit.runner.di

import distage.{Module, Roots}
import izumi.distage.framework.{CoreCheckableAppSimple, PlanCheckConfig, PlanCheckMaterializer}
import izumi.distage.model.definition.ModuleBase
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{TestAssertions, WiringAssertions}
import izumi.fundamentals.assertions.AssertionFailure
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object WiringFrontendFixtures {
  final class App extends CoreCheckableAppSimple[Identity] {
    val plans = new AtomicInteger(0)
    override def module: ModuleBase = { val _ = plans.incrementAndGet(); Module.empty }
    override def roots: Roots = Roots.Everything
  }

  def configuration: TestConfig = TestConfig.empty.copy(pluginConfig = PluginConfig.empty)

  def materializer(app: App, passed: Boolean): PlanCheckMaterializer[app.type, PlanCheckConfig.Any] =
    PlanCheckMaterializer(passed, Seq.empty, app, "*", "", "*", Some(false), Some(false), Some(false))

  private final class Assertions extends TestAssertions with WiringAssertions

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val assertions = new Assertions
    val app = new App
    val cfg = PlanCheckConfig.empty
    assertions.assertWiringCompileTime(app, cfg, checkAgainAtRuntime = false)(materializer(app, passed = true))
    verify("wiring assertion without runtime recheck acquires no planning input", app.plans.get() == 0)
    assertions.assertWiringCompileTime(app, cfg, checkAgainAtRuntime = true)(materializer(app, passed = true))
    verify("wiring assertion delegates exactly one requested runtime plan", app.plans.get() == 1)
    assertions.assertWiringRuntime(app, cfg)
    verify("runtime wiring assertion delegates to the actual application", app.plans.get() == 2)
    val _ = assertions.intercept[AssertionFailure](assertions.assertWiringCompileTime(app, cfg, checkAgainAtRuntime = true)(materializer(app, passed = false)))
    verify("failed compile-time wiring assertion prevents runtime recheck", app.plans.get() == 2)

    Vector(false, true).foldLeft(Future.successful(())) { (before, runtime) => before.flatMap { _ =>
      val selected = new App
      val identity = CatalogueIdentity(BuildId("wiring-frontends"), BuildTargetId("wiring"), CatalogueId(runtime.toString))
      val sink = FixtureSupport.silentSink()
      val session = new RunSession(identity, Vector(() => WiringFixtureSuite.make(selected, runtime)), context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      val expected = if (runtime) 2 else 1
      verify("wiring suite discovery registers only its requested checks", catalogue.tests.size == expected && selected.plans.get() == 0)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      session.execute(RunId("wiring-" + runtime), request).map { outcome =>
        verify("wiring suite executes every registered check successfully", outcome.successful && outcome.results.size == expected)
        verify("wiring suite runtime flag controls actual application planning", selected.plans.get() == (if (runtime) 1 else 0))
        println("DISTAGE_WIRING_FRONTEND_OK runtime=" + runtime + " tests=" + expected)
      }
    } }.flatMap(_ => WiringDefaultsFixtures.run(context, verify))
  }
}
