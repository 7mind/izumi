package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.ConfigInjectionOptions
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition.{ImplDef, Module}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.roles.{RoleAppLauncher, RolesInfo}
import com.github.pshirshov.izumi.distage.roles.services.IntegrationChecker.IntegrationCheckException
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.distage.roles.services.ResourceRewriter.RewriteRules
import com.github.pshirshov.izumi.distage.roles.services._
import com.github.pshirshov.izumi.distage.testkit.services.{IgnoreSupport, SuppressionSupport}
import com.github.pshirshov.izumi.fundamentals.platform.cli.RoleAppArguments
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

    val allRoots = function.get.diKeys.toSet ++ additionalRoots

    val planner = makePlanner(refineBindings(allRoots, appModule), injector)

    val plan = planner.makePlan(allRoots)

    try {
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
    } catch {
      case i: IntegrationCheckException =>
        suppressTheRestOfTestSuite()
        ignoreThisTest(Some(i.getMessage), Option(i.getCause))
    }
  }

  protected def loadEnvironment(config: AppConfig, logger: IzLogger): TestEnvironment

  protected def bootstrapLogLevel: Level = IzLogger.Level.Warn

  protected def additionalRoots: Set[DIKey] = Set.empty

  protected def makeLogger(): IzLogger = IzLogger.apply(bootstrapLogLevel)("phase" -> "test")

  protected def makeModuleProvider(config: AppConfig, lateLogger: IzLogger, roles: RolesInfo): ModuleProvider[F] = {
    // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
    new ModuleProviderImpl[F](
      lateLogger,
      config,
      roles,
      contextOptions(),
    )
  }

  protected def contextOptions(): ContextOptions = {
    ContextOptions(
      addGvDump = false,
      RewriteRules(),
      ConfigInjectionOptions(),
    )
  }

  protected def makePlanner(module: distage.ModuleBase, injector: Injector): RoleAppPlanner[F] = {
    new RoleAppPlannerImpl[F](module, injector)
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
