package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.ConfigInjectionOptions
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef, Module}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.provisioning.PlanInterpreter
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.IntegrationChecker.IntegrationCheckException
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.distage.roles.services.ResourceRewriter.RewriteRules
import com.github.pshirshov.izumi.distage.roles.services.StartupPlanExecutor.Filters
import com.github.pshirshov.izumi.distage.roles.services._
import com.github.pshirshov.izumi.distage.testkit.services.ExternalResourceProvider.{MemoizedInstance, OrderedFinalizer, PreparedShutdownRuntime}
import com.github.pshirshov.izumi.distage.testkit.services._
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.Level
import distage.config.AppConfig
import distage.{DIKey, Injector, ModuleBase}



abstract class DistageTestSupport[F[_] : TagK]
  extends DISyntax[F]
    with IgnoreSupport
    with SuppressionSupport {
  private lazy val erpInstance = externalResourceProvider

  protected def externalResourceProvider: ExternalResourceProvider = ExternalResourceProvider.Null

  protected def memoizationContextId: MemoizationContextId

  private def doMemoize(locator: Locator): Unit = {
    val fmap = locator.finalizers[F].zipWithIndex.map {
      case (f, idx) =>
        f.key -> OrderedFinalizer(f, idx)
    }.toMap
    locator
      .allInstances
      .foreach {
        ref =>
          externalResourceProvider.process(memoizationContextId, MemoizedInstance[Any](ref, fmap.get(ref.key)))
      }
  }

  protected final def dio(function: ProviderMagnet[F[_]]): Unit = {
    val logger = makeLogger()
    val loader = makeConfigLoader(logger)
    val config = loader.buildConfig()
    val env = loadEnvironment(config, logger)
    val options = contextOptions()
    val provider = makeModuleProvider(options, config, logger, env.roles)

    val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule
    val appModule = provider.appModules().merge overridenBy env.appModule
    val injector = Injector.Standard(bsModule)

    val allRoots = function.get.diKeys.toSet ++ additionalRoots

    val refinedBindings = refineBindings(allRoots, appModule)
    val withMemoized = applyMemoization(refinedBindings)
    val planner = makePlanner(options, withMemoized, injector, logger)

    val plan = planner.makePlan(allRoots)

    erpInstance.registerShutdownRuntime[F](PreparedShutdownRuntime[F](
      injector.produceF[Identity](plan.runtime),
      implicitly[TagK[F]]
    ))

    val filters = Filters[F](
      (finalizers: Seq[PlanInterpreter.Finalizer[F]]) => finalizers.filterNot(f => erpInstance.isMemoized(memoizationContextId, f.key)),
      (finalizers: Seq[PlanInterpreter.Finalizer[Identity]]) => finalizers.filterNot(f => erpInstance.isMemoized(memoizationContextId, f.key)),
    )

    try {
      makeExecutor(injector, logger)
        .execute[F](plan, filters) {
        (locator, effect) =>
          implicit val e: DIEffect[F] = effect

          for {
            _ <- DIEffect[F].maybeSuspend(doMemoize(locator))
            _ <- DIEffect[F].maybeSuspend(verifyTotalSuppression())
            _ <- DIEffect[F].maybeSuspend(beforeRun(locator))
            _ <- DIEffect[F].maybeSuspend(verifyTotalSuppression())
            _ <- locator.run(function)
          } yield {

          }


      }
    } catch {
      case i: IntegrationCheckException =>
        suppressTheRestOfTestSuite()
        ignoreThisTest(Some(i.getMessage), Option(i.getCause))
    } finally {
      val cacheSize = erpInstance.size(memoizationContextId)
      if (cacheSize > 0) {
        logger.info(s"${cacheSize -> "memoized instances"} in ${memoizationContextId -> "memoization context"}")
      }
    }
  }

  private def applyMemoization(refinedBindings: ModuleBase): ModuleBase = {
    refinedBindings.map {
      b =>
        erpInstance.getMemoized(memoizationContextId, b.key) match {
          case Some(value) =>
            val impltype = b match {
              case binding: Binding.ImplBinding =>
                binding.implementation.implType
              case binding: Binding.SetBinding =>
                binding match {
                  case e: Binding.SetElementBinding[_] =>
                    e.implementation.implType
                  case s: Binding.EmptySetBinding[_] =>
                    s.key.tpe
                }
            }
            val impl = ImplDef.InstanceImpl(impltype, value)
            SingletonBinding(b.key, impl, b.tags, b.origin)

          case None =>
            b
        }


    }
  }

  protected def loadEnvironment(config: AppConfig, logger: IzLogger): TestEnvironment

  protected def bootstrapLogLevel: Level = IzLogger.Level.Warn

  protected def additionalRoots: Set[DIKey] = Set.empty

  protected def makeLogger(): IzLogger = IzLogger.apply(bootstrapLogLevel)("phase" -> "test")

  protected def makeModuleProvider(options: ContextOptions, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo): ModuleProvider[F] = {
    // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
    new ModuleProviderImpl[F](
      lateLogger,
      config,
      roles,
      options,
    )
  }

  protected def contextOptions(): ContextOptions = {
    ContextOptions(
      addGvDump = false,
      warnOnCircularDeps = true,
      RewriteRules(),
      ConfigInjectionOptions(),
    )
  }

  protected def makePlanner(options: ContextOptions, module: distage.ModuleBase, injector: Injector, logger: IzLogger): RoleAppPlanner[F] = {
    new RoleAppPlannerImpl[F](options, module, injector, logger)
  }

  protected def makeExecutor(injector: Injector, logger: IzLogger): StartupPlanExecutor = {
    StartupPlanExecutor.default(logger, injector)
  }

  /** You can override this to e.g. skip test when certain external dependencies are not available **/
  protected def beforeRun(context: Locator): Unit = {
    context.discard()
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
    new ConfigLoaderLocalFilesystemImpl(logger, None, moreConfigs)
  }

  /** Override this to disable instantiation of fixture parameters that aren't bound in `makeBindings` */
  protected def refineBindings(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase = {
    val paramsModule = Module.make {
      (roots - DIKey.get[LocatorRef]).map {
        key =>
          SingletonBinding(key, ImplDef.TypeImpl(key.tpe))
      }
    }

    paramsModule overridenBy primaryModule
  }
}
