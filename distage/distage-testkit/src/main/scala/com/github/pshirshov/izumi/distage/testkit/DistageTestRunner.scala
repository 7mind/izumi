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

trait TODOMemoizeMe {}

class DistageTestRunner[F[_]](implicit val tagK: TagK[F]) {

  import DistageTestRunner._

  private val tests = scala.collection.mutable.ArrayBuffer[DistageTest[F]]()

  def register(test: DistageTest[F]): Unit = {
    tests += test
  }

  def run() = {
    val groups = tests.groupBy(_.environment)

    groups.foreach {
      case (env, group) =>
        val logger = makeLogger()
        val options = contextOptions()
        val loader = makeConfigLoader(logger)
        val config = loader.buildConfig()

        val provider = makeModuleProvider(options, config, logger, env.roles, env.activation)
        val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule overridenBy bootstrapOverride
        val appModule = provider.appModules().merge overridenBy env.appModule

        val injector = Injector.Standard(bsModule)
        val runtimeGcRoots: Set[DIKey] = Set(
          DIKey.get[DIEffectRunner[F]],
          DIKey.get[DIEffect[F]],
        )

        val runtimePlan = injector.plan(PlannerInput(appModule, runtimeGcRoots))

        val testplans = group.map {
          pm =>
            val keys = pm.test.get.diKeys.toSet
            pm -> injector.plan(PlannerInput(appModule, keys))
        }

        val sharedKeys = testplans.map(_._2).flatMap {
          plan =>
            plan.steps.filter(op => ExecutableOp.instanceType(op) weak_<:< SafeType.get[TODOMemoizeMe]).map(_.target)
        }.toSet -- runtimeGcRoots

        val sharedPlan = if (sharedKeys.nonEmpty) {
          injector.plan(PlannerInput(appModule.drop(runtimePlan.keys), sharedKeys))
        } else {
          emptyPlan(runtimePlan)
        }

        val sharedIntegrationKeys = sharedPlan.collectChildren[IntegrationCheck].map(_.target).toSet
        val sharedIntegrationPlan = if (sharedIntegrationKeys.nonEmpty) {
          // exclude runtime
          injector.plan(PlannerInput(appModule.drop(runtimePlan.keys), sharedIntegrationKeys))
        } else {
          emptyPlan(runtimePlan)
        }
        val sharedPlanNoInteg = if (sharedIntegrationKeys.nonEmpty) {
          injector.plan(PlannerInput(appModule.drop(sharedIntegrationPlan.keys ++ runtimePlan.keys), sharedKeys -- sharedIntegrationKeys))
        } else {
          sharedPlan
        }

        injector.produceF[Identity](runtimePlan).use {
          runtimeLocator =>
            val runner = runtimeLocator.get[DIEffectRunner[F]]
            implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

            runner.run {
              Injector.inherit(runtimeLocator).produceF[F](sharedIntegrationPlan).use {
                sharedIntegrationLocator =>
                  // TODO: global integration checks

                  Injector.inherit(sharedIntegrationLocator).produceF[F](sharedPlanNoInteg).use {
                    sharedLocator =>
                      val testInjector = Injector.inherit(sharedLocator)

                      effect.traverse_(testplans) {
                        case (pm, testplan) =>

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

                          testInjector.produceF[F](integrationPlan).use {
                            integLocator =>
                              // TODO: test-specific integration checks
                              Injector.inherit(integLocator).produceF[F](planNoInteg).use {
                                testLocator =>
                                  for {
                                    _ <- testLocator.run(pm.test)
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

  case class DistageTest[F[_]](test: ProviderMagnet[F[_]], environment: TestEnvironment)

}
