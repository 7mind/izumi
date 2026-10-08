package izumi.distage.testkit.runner.di

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.framework.{PlanCheckConfig, PlanCheckMaterializer}
import izumi.distage.testkit.runner.spec.{TestAssertions, WiringAssertions}

import scala.concurrent.{ExecutionContext, Future}

private[di] trait WiringDefaultsControls {
  def withConfig(app: WiringFrontendFixtures.App): TestSuite
  def withDefaults(app: WiringFrontendFixtures.App): TestSuite
  def assertion(app: WiringFrontendFixtures.App): Unit = {
    val cfg = PlanCheckConfig.empty
    val plan = PlanCheckMaterializer[app.type, cfg.type](true, Seq.empty, app, "*", "", "*", Some(false), Some(false), Some(false))
    val assertions = new TestAssertions with WiringAssertions {}
    assertions.assertWiringCompileTime(app, cfg)(plan)
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val direct = new WiringFrontendFixtures.App
    assertion(direct)
    verify("omitted wiring assertion runtime flag retains the legacy recheck", direct.plans.get() == 1)
    Vector("configured", "defaults").foldLeft(Future.successful(())) { (before, mode) => before.flatMap { _ =>
      val app = new WiringFrontendFixtures.App
      val factory = () => if (mode == "configured") withConfig(app) else withDefaults(app)
      val identity = CatalogueIdentity(BuildId("wiring-defaults"), BuildTargetId("wiring"), CatalogueId(mode))
      val sink = FixtureSupport.silentSink()
      val session = new RunSession(identity, Vector(factory), context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify("omitted wiring arguments register both legacy checks without planning", catalogue.tests.size == 2 && app.plans.get() == 0)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      session.execute(RunId("wiring-defaults-" + mode), request).map { outcome =>
        verify("default wiring checks execute successfully", outcome.successful && outcome.results.size == 2)
        verify("default wiring runtime recheck plans once", app.plans.get() == 1)
        println("DISTAGE_WIRING_DEFAULTS_OK mode=" + mode + " cases=2 planning=once")
      }
    }}
  }
}
