package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition.{ImplDef, Module}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.roles.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services._
import com.github.pshirshov.izumi.distage.testkit.services.{IgnoreSupport, SuppressionSupport}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.Level
import distage.config.AppConfig
import distage.{DIKey, Injector, ModuleBase, Tag}


abstract class DistageTestSupport[F[_] : TagK]
  extends IgnoreSupport
    with SuppressionSupport {

  protected final def di[T: Tag](function: T => F[_]): Unit = {
    val providerMagnet: ProviderMagnet[F[_]] = {
      x: T =>
        function(x)
    }
    di(providerMagnet)
  }

  protected final def di(function: ProviderMagnet[F[_]]): Unit = {
    val logger = makeLogger()
    val loader = makeConfigLoader(logger)
    val config = loader.buildConfig()
    val env = loadEnvironment(config, logger)
    val provider = makeModuleProvider(config, logger, env.roles)

    val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule
    val appModule = provider.appModules().merge overridenBy env.appModule
    val injector = Injector.Standard(bsModule)

    val allRoots = function.get.diKeys.toSet ++ additionalRoots ++ Set(
      RuntimeDIUniverse.DIKey.get[Finalizers.CloseablesFinalized],
    )

    val planner = makePlanner(refineBindings(allRoots, appModule), env.roles, injector)


    val plan = planner.makePlan(allRoots)

    makeExecutor(injector, logger)
      .execute[F](plan) {
      (locator, effect) =>
        implicit val e: DIEffect[F] = effect

        for {
          _ <- DIEffect[F].maybeSuspend(verifyTotalSuppression())
          _ <- DIEffect[F].maybeSuspend(beforeRun(locator))
          _ <- DIEffect[F].maybeSuspend(verifyTotalSuppression())
          _ <- locator.run(function)
        } yield {

        }

    }
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


  protected def loadEnvironment(config: AppConfig, logger: IzLogger): TestEnvironment

  protected def bootstrapLogLevel: Level = IzLogger.Level.Debug

  protected def additionalRoots: Set[DIKey] = Set.empty

  protected def makeLogger(): IzLogger = IzLogger.apply(bootstrapLogLevel)("phase" -> "test")

  // convenience helper for subclasses
  protected def loadRoles(logger: IzLogger): RolesInfo = {
    Quirks.discard(logger)
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
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

  protected def makePlanner(module: distage.ModuleBase, roles: RolesInfo, injector: Injector): RoleAppPlanner[F] = {
    new RoleAppPlannerImpl[F](module, roles, injector)
  }

  protected def makeModuleProvider(config: AppConfig, lateLogger: IzLogger, roles: RolesInfo): ModuleProvider[F] = {
    new ModuleProviderImpl[F](lateLogger, config, addGvDump = false, roles)
  }

  protected def makeExecutor(injector: Injector, logger: IzLogger): StartupPlanExecutor = {
    StartupPlanExecutor.default(logger, injector)
  }

  /** You can override this to e.g. skip test when certain external dependencies are not available **/
  protected def beforeRun(context: Locator): Unit = {
    context.discard()
  }
}
