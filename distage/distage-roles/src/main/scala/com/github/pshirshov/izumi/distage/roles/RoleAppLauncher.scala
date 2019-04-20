package com.github.pshirshov.izumi.distage.roles

import java.io.File

import com.github.pshirshov.izumi.distage.app.{BootstrapConfig, DiAppBootstrapException}
import com.github.pshirshov.izumi.distage.config.ResolvedConfig
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.{DependencyGraph, DependencyKind, PlanTopologyImmutable}
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.plugins.MergedPlugins
import com.github.pshirshov.izumi.distage.roles.cli.RoleAppArguments
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.services.PluginSource.AllLoadedPlugins
import com.github.pshirshov.izumi.distage.roles.services._
import com.github.pshirshov.izumi.distage.roles.services.cliparser.CLIParser
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage._

import scala.reflect.ClassTag


case class AppStartupPlans(runtime: OrderedPlan, integration: OrderedPlan, integrationKeys: Set[DIKey], app: OrderedPlan)

/**
  * Application flow:
  * 1. Parse commandline parameters
  * 2. Create "early logger" (console sink & configurable log level)
  * 3. Show startup banner
  * 4. Load raw config
  * 5. Create "late logger" using config
  * 6. Enumerate app plugins and bootstrap plugins
  * 7. Enumerate available roles, show role info and and apply merge strategy/conflict resolution
  * 8. Validate loaded roles (for non-emptyness and conflicts between bootstrap and app plugins)
  * 9. Build plan for DIEffect runner
  * 10. Build plan for integration checks
  * 11. Build plan for application
  * 12. Run roles
  * 13. Run services
  * 14. Await application termination
  * 15. Close autocloseables
  * 16. Shutdown executors
  */
abstract class RoleAppLauncher[F[_] : TagK : DIEffect] {

  import RoleAppLauncher._

  private val loggers = new EarlyLoggers()

  final def launch(parameters: RoleAppArguments, bootstrapConfig: BootstrapConfig, referenceLibraryInfo: Seq[Using]): Unit = {
    val earlyLogger = loggers.makeEarlyLogger(parameters)
    showBanner(earlyLogger, referenceLibraryInfo)
    val config = makeConfigLoader(earlyLogger, parameters).buildConfig()
    val lateLogger = loggers.makeLateLogger(parameters, earlyLogger, config)
    val plugins = makePluginLoader(bootstrapConfig).load()

    val roles = loadRoles(parameters, lateLogger, plugins)
    val mergeStrategy = makeMergeProvider(lateLogger, parameters).mergeStrategy(plugins, roles)
    val defBs = mergeStrategy.merge(plugins.bootstrap)
    val defApp = mergeStrategy.merge(plugins.app)
    validate(defBs, defApp)
    lateLogger.info(s"Loaded ${defApp.definition.bindings.size -> "app bindings"} and ${defBs.definition.bindings.size -> "bootstrap bindings"}...")

    val moduleProvider = makeModuleProvider(parameters, config, lateLogger, roles)
    val bsModule = moduleProvider.bootstrapModules().merge overridenBy defBs.definition
    val appModule = moduleProvider.appModules().merge overridenBy defApp.definition

    val injector = Injector.Standard(bsModule)

    val appPlan = makePlans(roles, defBs, defApp, appModule, injector)

    lateLogger.info(s"Planning finished. ${appPlan.app.keys.size -> "main ops"}, ${appPlan.integration.keys.size -> "integration ops"}, ${appPlan.runtime.keys.size -> "runtime ops"}")

    runPlan(parameters, lateLogger, roles, injector, appPlan)
  }

  protected def runPlan(parameters: RoleAppArguments, lateLogger: IzLogger, roles: RolesInfo, injector: Injector, appPlan: AppStartupPlans): Unit = {

    injector.produce(appPlan.runtime).use {
      runtimeLocator =>
        val runner = runtimeLocator.get[DIEffectRunner[F]]

        runner.run {
          Injector.inherit(runtimeLocator).produceF[F](appPlan.integration).use {
            integrationLocator =>
              makeIntegrationCheck(lateLogger).check(appPlan.integrationKeys, integrationLocator)

              Injector.inherit(integrationLocator).produceF[F](appPlan.app).use {
                rolesLocator =>
                  val roleIndex = getRoleIndex(roles, rolesLocator)

                  for {
                    _ <- runTasks(roleIndex, lateLogger, parameters)
                    _ <- runRoles(roleIndex, lateLogger, parameters)
                  } yield {

                  }
              }
          }
        }
    }
    hook.release()
  }

  protected def makePlans(roles: RolesInfo, defBs: MergedPlugins, defApp: MergedPlugins, appModule: Module, injector: Injector): AppStartupPlans = {
    val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[DIEffectRunner[F]],
      DIKey.get[Finalizers.ExecutorsFinalized],
    )
    val runtimePlan = injector.plan(appModule, runtimeGcRoots)

    val appGcRoots = gcRoots(defBs, defApp, roles)
    val rolesPlan = injector.plan(appModule, appGcRoots)

    val integrationComponents = rolesPlan.collectChildren[IntegrationCheck].map(_.target).toSet

    val integrationPlan = if (integrationComponents.nonEmpty) {
      // exclude runtime
      injector.plan(PlannerInput(appModule.drop(runtimePlan.keys), integrationComponents))
    } else {
      emptyPlan(runtimePlan)
    }

    val refinedRolesPlan = if (appGcRoots.nonEmpty) {
      // exclude runtime & integrations
      injector.plan(PlannerInput(appModule.drop(integrationPlan.keys), appGcRoots))
    } else {
      emptyPlan(runtimePlan)
    }
    //    println("====")
    //    println(runtimePlan.render())
    //    println("----")
    //    println("====")
    //    println(integrationPlan.render())
    //    println("----")
    //    println("====")
    //    println(refinedRolesPlan.render())
    //    println("----")

    AppStartupPlans(runtimePlan, integrationPlan, integrationComponents, refinedRolesPlan)
  }

  private def emptyPlan(runtimePlan: OrderedPlan): OrderedPlan = {
    OrderedPlan(runtimePlan.definition, Vector.empty, Set.empty, PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required)))
  }

  protected def runRoles(index: Map[String, Object], lateLogger: IzLogger, parameters: RoleAppArguments): F[Unit] = {
    val rolesToRun = parameters.roles.flatMap {
      r =>
        index.get(r.role) match {
          case Some(_: RoleTask2[F]) =>
            Seq.empty
          case Some(value: RoleService2[F]) =>
            Seq(value -> r)
          case _ =>
            throw new DiAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} is missing")
        }
    }


    if (rolesToRun.nonEmpty) {
      lateLogger.info(s"Going to run: ${rolesToRun.size -> "roles"}")

      val tt = rolesToRun.map {
        case (task, cfg) =>
          task.start(cfg.roleParameters, cfg.freeArgs)
      }

      tt.foldLeft((_: Unit) => {
        hook.await(lateLogger)

      }) {
        case (acc, r) =>
          _ => r.use(acc)
      }(())
    } else {
      DIEffect[F].maybeSuspend(lateLogger.info("No services to run, exiting..."))
    }
  }

  protected val hook: ApplicationShutdownStrategy[F]

  protected def runTasks(index: Map[String, Object], lateLogger: IzLogger, parameters: RoleAppArguments): F[Unit] = {
    val tasksToRun = parameters.roles.flatMap {
      r =>
        index.get(r.role) match {
          case Some(value: RoleTask2[F]) =>
            Seq(value -> r)
          case Some(_: RoleService2[F]) =>
            Seq.empty
          case _ =>
            throw new DiAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} is missing")
        }
    }

    lateLogger.info(s"Going to run: ${tasksToRun.size -> "tasks"}")

    DIEffect[F].traverse_(tasksToRun) {
      case (task, cfg) =>
        task.start(cfg.roleParameters, cfg.freeArgs)
    }
  }

  private def getRoleIndex(roles: RolesInfo, rolesLocator: Locator): Map[String, AbstractRole] = {
    val tasks = rolesLocator.get[Set[RoleTask2[F]]] ++ rolesLocator.get[Set[RoleService2[F]]]
    val roleClassMap = roles.availableRoleBindings.map {
      b =>
        b.runtimeClass.asInstanceOf[AnyRef] -> b.name
    }.toMap

    val itasks = tasks.map {
      t =>
        roleClassMap.get(t.getClass.asInstanceOf[AnyRef]) match {
          case Some(value) =>
            value -> t
          case None =>
            throw new DiAppBootstrapException(s"Inconsistent state: task $t is missing from roles metadata")
        }
    }.toMap
    itasks
  }

  private def makeIntegrationCheck(lateLogger: IzLogger): IntegrationChecker = {
    new IntegrationCheckerImpl(lateLogger)
  }

  protected def gcRoots(bs: MergedPlugins, app: MergedPlugins, rolesInfo: RolesInfo): Set[DIKey] = {
    Quirks.discard(bs, app)
    rolesInfo.requiredComponents ++ Set(
      RuntimeDIUniverse.DIKey.get[ResolvedConfig],
      RuntimeDIUniverse.DIKey.get[Set[RoleService2[F]]],
      RuntimeDIUniverse.DIKey.get[Finalizers.CloseablesFinalized],
    )
  }

  protected def makeModuleProvider(parameters: RoleAppArguments, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo): ModuleProvider[F] = {
    new ModuleProviderImpl[F](lateLogger, parameters, config, roles)
  }

  protected def loadRoles(parameters: RoleAppArguments, lateLogger: IzLogger, plugins: AllLoadedPlugins): RolesInfo = {
    val activeRoleNames = parameters.roles.map(_.role).toSet
    val mp = MirrorProvider.Impl
    val roleProvider: RoleProvider[F] = new RoleProviderImpl(lateLogger, activeRoleNames, mp)
    val bindings = plugins.app.flatMap(_.bindings)
    val bsBindings = plugins.app.flatMap(_.bindings)
    lateLogger.info(s"Available ${plugins.app.size -> "app plugins"} with ${bindings.size -> "app bindings"} and ${plugins.bootstrap.size -> "bootstrap plugins"} with ${bsBindings.size -> "bootstrap bindings"} ...")
    val roles: RolesInfo = roleProvider.getInfo(bindings)

    printRoleInfo(lateLogger, roles)
    roles
  }

  protected def printRoleInfo(logger: IzLogger, roles: RolesInfo): Unit = {
    val availableRoleInfo = roles.availableRoleBindings.map {
      r =>
        s"${r.name}, ${r.tpe}, source=${r.source.getOrElse("N/A")}"
    }.sorted
    logger.info(s"Available ${availableRoleInfo.mkString("\n - ", "\n - ", "") -> "roles"}")
    logger.info(s"Requested ${roles.requiredRoleBindings.map(_.name).mkString("\n - ", "\n - ", "") -> "roles"}")
  }


  protected def showBanner(logger: IzLogger, referenceLibraries: Seq[Using]): this.type = {
    val withIzumi = referenceLibraries :+ Using("izumi-r2", classOf[ConfigLoader])
    showDepData(logger, "Application is about to start", this.getClass)
    withIzumi.foreach { u => showDepData(logger, s"... using ${u.libraryName}", u.clazz) }
    this
  }

  private def showDepData(logger: IzLogger, msg: String, clazz: Class[_]): Unit = {
    val mf = IzManifest.manifest()(ClassTag(clazz)).map(IzManifest.read)
    val details = mf.getOrElse("{No version data}")
    logger.info(s"$msg : $details")
  }

  protected def validate(bootstrapAutoDef: MergedPlugins, appDef: MergedPlugins): Unit = {
    val conflicts = bootstrapAutoDef.definition.keys.intersect(appDef.definition.keys)
    if (conflicts.nonEmpty) {
      throw new DiAppBootstrapException(s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating...")
    }

    if (appDef.definition.bindings.isEmpty) {
      throw new DiAppBootstrapException("Empty app object graph. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
    }
  }

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    new PluginSourceImpl(bootstrapConfig)
  }

  protected def makeConfigLoader(logger: IzLogger, parameters: RoleAppArguments): ConfigLoader = {
    val maybeGlobalConfig = parameters.globalParameters.values.find(p => configParam.matches(p.name)).map(v => new File(v.value))
    val roleConfigs = parameters.roles.map {
      r =>
        r.role -> r.roleParameters.values.find(p => configParam.matches(p.name)).map(v => new File(v.value))
    }
    new ConfigLoaderLocalFilesystemImpl(logger, maybeGlobalConfig, roleConfigs.toMap)
  }

  protected def makeMergeProvider(lateLogger: IzLogger, parameters: RoleAppArguments): MergeProvider = {
    new MergeProviderImpl(lateLogger, parameters)
  }
}

object RoleAppLauncher {

  final val logLevelRootParam = CLIParser.ParameterNameDef("log-level-root", "ll")
  final val logFormatParam = CLIParser.ParameterNameDef("log-format", "logs")
  final val configParam = CLIParser.ParameterNameDef("config", "c")
  final val dumpContext = CLIParser.ParameterNameDef("debug-dump-graph")

  @deprecated("We should stop using tags", "2019-04-19")
  final val useDummies = CLIParser.ParameterNameDef("mode:dummies")
}
