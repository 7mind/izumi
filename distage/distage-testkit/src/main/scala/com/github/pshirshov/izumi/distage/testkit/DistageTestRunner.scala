package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.ConfigInjectionOptions
import com.github.pshirshov.izumi.distage.model.GCMode
import com.github.pshirshov.izumi.distage.model.definition.BootstrapModule
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.model.plan.{DependencyGraph, DependencyKind, ExecutableOp, PlanTopologyImmutable}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.roles.model.{AppActivation, IntegrationCheck}
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.{ConfigLoader, ConfigLoaderLocalFSImpl, ModuleProvider, ModuleProviderImpl}
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.distage.roles.services.ResourceRewriter.RewriteRules
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.Level
import distage.config.AppConfig
import distage.{DIKey, Injector, OrderedPlan, PlannerInput}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.testkit.DistageTestRunner.TestReporter

// a marker trait just for our demo purposes. All the entities inheriting this trait will be shared between tests contexts
trait TODOMemoizeMe {}

class DistageTestRunner[F[_]](reporter: TestReporter)(implicit val tagK: TagK[F]) {

  import DistageTestRunner._

  private val tests = scala.collection.mutable.ArrayBuffer[DistageTest[F]]()

  def register(test: DistageTest[F]): Unit = {
    reporter.testStatus(test.id, TestStatus.Scheduled)
    tests += test
  }

  def run(): Unit = {
    val groups = tests.groupBy(_.environment)

    groups.foreach {
      case (env, group) =>
        val logger = makeLogger()
        val options = contextOptions()
        val loader = makeConfigLoader(logger)
        val config = loader.buildConfig()

        // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
        val provider = makeModuleProvider(options, config, logger, env.roles, env.activation)
        val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule overridenBy bootstrapOverride
        val appModule = provider.appModules().merge overridenBy env.appModule

        val injector = Injector.Standard(bsModule)

        // first we need to plan runtime for our monad. Identity is also supported
        val runtimeGcRoots: Set[DIKey] = Set(
          DIKey.get[DIEffectRunner[F]],
          DIKey.get[DIEffect[F]],
        )

        val runtimePlan = injector.plan(PlannerInput(appModule, runtimeGcRoots))

        // here we plan all the job for each individual test
        val testplans = group.map {
          pm =>
            val keys = pm.test.get.diKeys.toSet
            pm -> injector.plan(PlannerInput(appModule, keys))
        }

        // here we find all the shared components in each of our individual tests
        val sharedKeys = testplans.map(_._2).flatMap {
          plan =>
            plan.steps.filter(op => ExecutableOp.instanceType(op) weak_<:< SafeType.get[TODOMemoizeMe]).map(_.target)
        }.toSet -- runtimeGcRoots

        // here we build merged plan for all our shared components
        val sharedPlan = if (sharedKeys.nonEmpty) {
          injector.plan(PlannerInput(appModule.drop(runtimePlan.keys), sharedKeys))
        } else {
          emptyPlan(runtimePlan)
        }

        // here we extract integration checks out of our shared components plan and build it
        val sharedIntegrationKeys = sharedPlan.collectChildren[IntegrationCheck].map(_.target).toSet
        val sharedIntegrationPlan = if (sharedIntegrationKeys.nonEmpty) {
          // exclude runtime
          injector.plan(PlannerInput(appModule.drop(runtimePlan.keys), sharedIntegrationKeys))
        } else {
          emptyPlan(runtimePlan)
        }

        // and here we build our final plan for shared components, with integration components excluded
        val sharedPlanNoInteg = if (sharedIntegrationKeys.nonEmpty) {
          injector.plan(PlannerInput(appModule.drop(sharedIntegrationPlan.keys ++ runtimePlan.keys), sharedKeys -- sharedIntegrationKeys))
        } else {
          sharedPlan
        }

        // first we produce our Monad's runtime
        injector.produceF[Identity](runtimePlan).use {
          runtimeLocator =>
            val runner = runtimeLocator.get[DIEffectRunner[F]]
            implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

            runner.run {
              // now we produce integration components for our shared plan
              Injector.inherit(runtimeLocator).produceF[F](sharedIntegrationPlan).use {
                sharedIntegrationLocator =>
                  // TODO: here we should perform global integration checks and ignore all the tests in case they fail

                  // here we produce our shared plan
                  Injector.inherit(sharedIntegrationLocator).produceF[F](sharedPlanNoInteg).use {
                    sharedLocator =>
                      val testInjector = Injector.inherit(sharedLocator)

                      // now we are ready to run each individual test
                      // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
                      effect.traverse_(testplans) {
                        case (test, testplan) =>

                          // we repeat the same approach with splitting our test plan into two plans - primary and integration
                          val allSharedKeys = sharedLocator.allInstances.map(_.key).toSet
                          val integrationKeys = testplan.collectChildren[IntegrationCheck].map(_.target).toSet -- allSharedKeys
                          val integrationPlan = if (integrationKeys.nonEmpty) {
                            // exclude runtime
                            testInjector.plan(PlannerInput(appModule.drop(allSharedKeys), integrationKeys))
                          } else {
                            emptyPlan(runtimePlan)
                          }
                          val planNoInteg = if (integrationKeys.nonEmpty) {
                            testInjector.plan(PlannerInput(appModule.drop(allSharedKeys ++ integrationPlan.keys), testplan.keys -- allSharedKeys -- integrationKeys))
                          } else {
                            testplan
                          }

                          // we are ready to run the test, finally
                          testInjector.produceF[F](integrationPlan).use {
                            integLocator =>
                              // TODO: test-specific integration checks
                              Injector.inherit(integLocator).produceF[F](planNoInteg).use {
                                testLocator =>
                                  def doRun = for {
                                    _ <- testLocator.run(test.test)
                                    _ <- effect.maybeSuspend(reporter.testStatus(test.id, TestStatus.Succeed))
                                  } yield {
                                  }
                                  val doRecover: PartialFunction[Throwable, F[Unit]] = {
                                    // TODO: here we may also ignore individual tests
                                    case t: Throwable =>
                                      reporter.testStatus(test.id, TestStatus.Failed(t))
                                      effect.pure(())
                                  }

                                  for {
                                    _ <- effect.maybeSuspend(reporter.testStatus(test.id, TestStatus.Running))
                                    _ <- effect.definitelyRecover(doRun, doRecover)
                                  } yield {

                                  }

                              }
                          }

                      }

                  }
              }
            }
        }
    }
  }

  private def emptyPlan(runtimePlan: OrderedPlan): OrderedPlan = {
    OrderedPlan(runtimePlan.definition, Vector.empty, GCMode.NoGC, PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required)))
  }

  protected def bootstrapOverride: BootstrapModule = BootstrapModule.empty

  protected def bootstrapLogLevel: Level = IzLogger.Level.Warn

  protected def makeLogger(): IzLogger = IzLogger.apply(bootstrapLogLevel)("phase" -> "test")

  protected def contextOptions(): ContextOptions = {
    ContextOptions(
      addGvDump = false,
      warnOnCircularDeps = true,
      RewriteRules(),
      ConfigInjectionOptions(),
    )
  }

  protected def makeConfigLoader(logger: IzLogger): ConfigLoader = {
    val thisClass = this.getClass
    val pname = s"${thisClass.getPackage.getName}"
    val lastPackage = pname.split('.').last
    val classname = thisClass.getName

    val moreConfigs = Map(
      s"$lastPackage-test" -> None,
      s"$classname-test" -> None,
    )
    new ConfigLoaderLocalFSImpl(logger, None, moreConfigs)
  }


  protected def makeModuleProvider(options: ContextOptions, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo, activation: AppActivation): ModuleProvider[F] = {
    // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
    new ModuleProviderImpl[F](
      lateLogger,
      config,
      roles,
      options,
      RawAppArgs.empty,
      activation,
    )
  }
}

object DistageTestRunner {

  case class TestId(string: String)

  case class DistageTest[F[_]](id: TestId, test: ProviderMagnet[F[_]], environment: TestEnvironment)

  sealed trait TestStatus

  object TestStatus {

    case object Scheduled extends TestStatus

    case object Running extends TestStatus

    case object Succeed extends TestStatus

    case class Failed(t: Throwable) extends TestStatus

    case object Ignored extends TestStatus

  }

  trait TestReporter {
    def testStatus(test: DistageTestRunner.TestId, testStatus: TestStatus)
  }

}
