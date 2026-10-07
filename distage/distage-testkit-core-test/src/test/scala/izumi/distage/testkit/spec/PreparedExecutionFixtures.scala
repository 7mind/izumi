package izumi.distage.testkit.spec

import distage.*
import izumi.distage.config.model.AppConfig
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.plugins.merge.SimplePluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.DistageTestRunner
import izumi.distage.testkit.runner.impl.services.TestConfigLoader
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.logstage.api.IzLogger

import java.util.concurrent.atomic.AtomicInteger
import scala.util.{Failure, Success, Try}

private[spec] object PreparedExecutionFixtures {
  def checks(): Vector[(String, Boolean)] = Vector(
    Request("success", execute = true, failBody = false, failRelease = false, missingDependency = false, failConfig = false),
    Request("abandoned", execute = false, failBody = false, failRelease = false, missingDependency = false, failConfig = false),
    Request("body-failure", execute = true, failBody = true, failRelease = false, missingDependency = false, failConfig = false),
    Request("release-failure", execute = true, failBody = false, failRelease = true, missingDependency = false, failConfig = false),
    Request("setup-failure", execute = true, failBody = false, failRelease = false, missingDependency = true, failConfig = false),
    Request("configuration-failure", execute = false, failBody = false, failRelease = false, missingDependency = false, failConfig = true),
  ).flatMap(check) ++ ownershipChecks()

  private def ownershipChecks(): Vector[(String, Boolean)] = {
    val ownerReporter = new RecordingReporter(new AtomicInteger(0), new AtomicInteger(0))
    val otherReporter = new RecordingReporter(new AtomicInteger(0), new AtomicInteger(0))
    Injector.withoutDefaultModule[Identity]().produceRun(new TestkitRunnerModule[Identity](ownerReporter, _ => false)) {
      (owner: DistageTestRunner[Identity]) =>
        Injector.withoutDefaultModule[Identity]().produceRun(new TestkitRunnerModule[Identity](otherReporter, _ => false)) {
          (other: DistageTestRunner[Identity]) =>
            val prepared = owner.plan(Nil)
            val foreign = Try(other.runPrepared(prepared))
            val foreignRejected = foreign.isFailure && ownerReporter.begins == 0 && otherReporter.begins == 0 && otherReporter.ends == 0
            val accepted = Try(owner.runPrepared(prepared))
            val acceptedOnce = accepted.isSuccess && ownerReporter.begins == 1 && ownerReporter.ends == 1
            val repeated = Try(owner.runPrepared(prepared))
            Vector(
              "prepared execution rejects another owner before reporting" -> foreignRejected,
              "rejected ownership leaves the plan executable by its owner" -> acceptedOnce,
              "prepared execution rejects reuse before reporting" -> (repeated.isFailure && ownerReporter.begins == 1 && ownerReporter.ends == 1),
            )
        }
    }
  }

  private def check(request: Request): Vector[(String, Boolean)] = {
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val bodies = new AtomicInteger(0)
    val configs = new AtomicInteger(0)
    val bodyFailure = new IllegalStateException("prepared body failure")
    val releaseFailure = new IllegalStateException("prepared resource finalizer failure")
    val configFailure = new IllegalStateException("prepared configuration failure")
    val definitions = new ModuleDef {
      make[Resource].fromResource {
        () => Lifecycle.makeSimple {
          val _ = acquired.incrementAndGet()
          new Resource
        } { _ =>
          val _ = released.incrementAndGet()
          if (request.failRelease) throw releaseFailure
        }
      }
    }
    val config = TestConfig.empty.copy(
      pluginConfig = PluginConfig.constUnchecked(definitions),
      memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
      activationStrategy = TestActivationStrategy.IgnoreConfig,
      parallelEnvs = TestConfig.Parallelism.Sequential,
      parallelSuites = TestConfig.Parallelism.Sequential,
      parallelTests = TestConfig.Parallelism.Sequential,
    )
    val roles = RolesInfo(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, Set.empty)
    val environment = new TestEnvironmentFactory.Impl().create[Identity](config, new PluginLoaderDefaultImpl(), roles, SimplePluginMergeStrategy, TagK[Identity], () => DefaultModule.empty[Identity].module)
    val tests = Vector.tabulate(2) { index =>
      val suite = SuiteId("PreparedExecution" + request.variant + index)
      val body = if (request.missingDependency) Functoid { (_: Missing) => () }
      else Functoid { (_: Resource) =>
        val _ = bodies.incrementAndGet()
        if (request.failBody && index == 0) throw bodyFailure
      }
      DistageTest[Identity](body, environment,
        TestMeta(TestId(Seq("executes its body"), suite), SourceFilePosition("PreparedExecutionFixtures.scala", 1), index.toLong),
        SuiteMeta(suite, suite.suiteId, suite.suiteId)).asInstanceOf[DistageTest[AnyF]]
    }
    val reporter = new RecordingReporter(acquired, released)
    val overrides = new ModuleDef {
      make[TestConfigLoader].fromValue(new TestConfigLoader {
        override def loadConfig(env: TestEnvironment, logger: IzLogger): AppConfig = {
          val _ = configs.incrementAndGet()
          if (request.failConfig) throw configFailure
          AppConfig.empty
        }
      })
    }
    val before = Injector.withoutDefaultModule[Identity]().produceRun(new TestkitRunnerModule[Identity](reporter, _ => false) overriddenBy overrides) {
      (runner: DistageTestRunner[Identity]) =>
        val planning = Try(runner.plan(tests))
        val resourceFree = acquired.get() == 0 && released.get() == 0 && bodies.get() == 0 && reporter.begins == 0 && reporter.statuses.isEmpty
        val exactPlan = planning match {
          case Success(prepared) =>
            val planned = prepared.planned
            val plannedIdentities = planned.out.good.flatMap(_.envs.values).flatMap(tree => tree.allTests.map(_.test.meta.test.id) ++ tree.allFailures.map(_.test.meta.test.id)) ++ planned.out.bad.flatMap(_._1.map(_.meta.test.id))
            !request.failConfig && plannedIdentities.toSet == tests.map(_.meta.test.id).toSet && configs.get() == 1
          case Failure(cause) => request.failConfig && (cause eq configFailure) && configs.get() == 1
        }
        val result = planning.flatMap { prepared =>
          if (request.execute) Try(runner.runPrepared(prepared)).map(_ => ()) else Success(())
        }
        (resourceFree, exactPlan, result, planning.isFailure)
    }
    val executionExpected = request.execute && !request.missingDependency
    val resources = if (executionExpected) 1 else 0
    val expectedBodies = if (executionExpected) 2 else 0
    val expectedSuccesses = if (executionExpected) { if (request.failBody) 1 else 2 } else 0
    val statuses = reporter.statuses
    val outcomeMatches = if (request.failConfig) before._3.failed.toOption.exists(_ eq configFailure) && reporter.begins == 0 && reporter.ends == 0
    else if (request.failRelease) before._3.failed.toOption.exists(_ eq releaseFailure) && reporter.ends == 0
    else before._3.isSuccess && reporter.ends == (if (request.execute) 1 else 0)
    val statusMatches = if (request.missingDependency) statuses.size == 2 && statuses.forall {
      case value: TestStatus.Failed => value.cause.isInstanceOf[IndividualTestResult.InstantiationFailure]
      case _ => false
    }
    else statuses.collect { case value: TestStatus.Failed => value.throwableCause }.toVector == (if (request.failBody) Vector(bodyFailure) else Vector.empty)
    println("PREPARED_EXECUTION variant=" + request.variant + " configs=" + configs.get() + " acquired=" + acquired.get() + " released=" + released.get() +
      " bodies=" + bodies.get() + " successes=" + statuses.count(_.isInstanceOf[TestStatus.Succeed]) + " begins=" + reporter.begins + " ends=" + reporter.ends + " resultSuccess=" + before._3.isSuccess + " planningFailed=" + before._4 + " statuses=" + statuses.map(_.getClass.getSimpleName).mkString(","))
    Vector(
      (request.variant + " planning neither provisions nor reports execution") -> before._1,
      (request.variant + " planning preserves selected identities or its original failure") -> before._2,
      (request.variant + " execution uses the prepared configuration snapshot") -> (configs.get() == 1),
      (request.variant + " resource lifetime and bodies match its execution") -> (acquired.get() == resources && released.get() == resources && bodies.get() == expectedBodies),
      (request.variant + " completion preserves body and finalizer outcomes") -> (outcomeMatches && statusMatches && statuses.count(_.isInstanceOf[TestStatus.Succeed]) == expectedSuccesses),
      (request.variant + " scope completion follows resource release") -> reporter.completedAfterRelease,
    )
  }

  private final case class Request(variant: String, execute: Boolean, failBody: Boolean, failRelease: Boolean, missingDependency: Boolean, failConfig: Boolean)
  final class Resource
  final class Missing

  private final class RecordingReporter(acquired: AtomicInteger, released: AtomicInteger) extends TestReporter {
    var begins = 0
    var ends = 0
    var completedAfterRelease = true
    var statuses = Vector.empty[TestStatus.Done]
    override def beginScope(id: ScopeId): Unit = { begins += 1 }
    override def endScope(id: ScopeId): Unit = { ends += 1; completedAfterRelease = acquired.get() == released.get() }
    override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def beginSuite(scope: ScopeId, depth: Int, suite: SuiteMeta): Unit = ()
    override def endSuite(scope: ScopeId, depth: Int, suite: SuiteMeta): Unit = ()
    override def testSetupStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: TestStatus.Setup): Unit = status match {
      case done: TestStatus.Done => statuses :+= done
      case _ => ()
    }
    override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: TestStatus): Unit = status match {
      case done: TestStatus.Done => statuses :+= done
      case _ => ()
    }
  }
}
