package com.github.pshirshov.izumi.distage.roles.role2

import java.io.File

import com.github.pshirshov.izumi.distage.app.{BootstrapConfig, DiAppBootstrapException}
import com.github.pshirshov.izumi.distage.config.ResolvedConfig
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.plugins.MergedPlugins
import com.github.pshirshov.izumi.distage.roles.cli.{Parameters, RoleAppArguments, RoleArg}
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.launcher.{RoleProvider, RoleProviderImpl}
import com.github.pshirshov.izumi.distage.roles.role2.PluginSource.AllLoadedPlugins
import com.github.pshirshov.izumi.distage.roles.role2.parser.CLIParser
import com.github.pshirshov.izumi.distage.roles.{IntegrationCheck, RoleTask2, RolesInfo}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage._

import scala.reflect.ClassTag

trait DIEffectRunner[F[_]] {
  def run[A](f: F[A]): A
}

object DIEffectRunner {

  implicit object IdentityDIEffectRunner extends DIEffectRunner[Identity] {
    override def run[A](f: Identity[A]): A = f
  }

}


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
  * // partial plans: get F runtime
  * // partial plans: get ICs
  * // partial plans: run app
  */
abstract class RoleAppLauncher[F[_] : TagK : DIEffect] {

  import RoleAppLauncher._

  private val loggers = new EarlyLoggers()

  protected def entrypoint(provisioned: Locator): F[Unit]

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

    val runtimeGcRoots: Set[DIKey] = Set(DIKey.get[DIEffectRunner[F]])
    val runtimePlan = injector.plan(appModule, runtimeGcRoots)

    // TODO: explode on empty roots
    val appGcRoots = gcRoots(defBs, defApp, roles)
    val rolesPlan = injector.plan(appModule, appGcRoots)

    val integrationComponents = rolesPlan.collectChildren[IntegrationCheck].map(_.target).toSet
    val integrationPlan = injector.plan(PlannerInput(appModule.drop(runtimePlan.keys), integrationComponents)) // exclude runtime

    val refinedRolesPlan = injector.plan(PlannerInput(appModule.drop(integrationPlan.keys), appGcRoots)) // exclude runtime & integrations

    lateLogger.info(s"Planning finished. ${refinedRolesPlan.keys.size -> "keys in main plan"}, ${integrationPlan.keys.size -> "keys in integration plan"}, ${runtimePlan.keys.size -> "keys in runtime plan"}")
    //    println("====")
    //    println(runtimePlan.render())
    //    println("----")
    //    println("====")
    //    println(integrationPlan.render())
    //    println("----")
    //    println("====")
    //    println(refinedRolesPlan.render())
    //    println("----")

    injector.produce(runtimePlan).use {
      runtimeLocator =>
        val runner = runtimeLocator.get[DIEffectRunner[F]]

        runner.run(

          Injector.inherit(runtimeLocator).produceF[F](integrationPlan).use {
            integrationLocator =>
              makeIntegrationCheck(lateLogger).check(integrationComponents, integrationLocator)

              Injector.inherit(integrationLocator).produceF[F](refinedRolesPlan).use {
                rolesLocator =>
                  runTasks(lateLogger, parameters, roles, rolesLocator)

                  lateLogger.info(s"Initialization finished, passing to the entrypoint")
                  entrypoint(rolesLocator)
              }
          }
        )
    }
  }


  protected def runTasks(lateLogger: IzLogger, parameters: RoleAppArguments, roles: RolesInfo, rolesLocator: Locator): Unit = {
    val tasks = rolesLocator.get[Set[RoleTask2]]
    lateLogger.info(s"Going to run: ${tasks.size -> "tasks"}")

    val roleClassMap = roles.availableRoleBindings.map {
      b =>
        b.runtimeClass.asInstanceOf[AnyRef] -> b.name
    }.toMap

    val params = parameters.roles.map {
      r =>
        r.role -> r
    }.toMap

    tasks.foreach {
      task =>
        val name = roleClassMap.get(task.getClass) match {
          case Some(value) =>
            value
          case None =>
            throw new DiAppBootstrapException(s"Inconsistent state: task $task is missing from roles metadata")
        }

        val cfg = params.getOrElse(name, RoleArg(name, Parameters.empty, Vector.empty))
        task.start(cfg.roleParameters, cfg.freeArgs)
    }
  }

  private def makeIntegrationCheck(lateLogger: IzLogger): IntegrationChecker = {
    new IntegrationCheckerImpl(lateLogger)
  }

  protected def gcRoots(bs: MergedPlugins, app: MergedPlugins, rolesInfo: RolesInfo): Set[DIKey] = {
    Quirks.discard(bs, app)
    rolesInfo.requiredComponents ++ Set(RuntimeDIUniverse.DIKey.get[ResolvedConfig])
  }

  protected def makeModuleProvider(parameters: RoleAppArguments, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo): ModuleProvider = {
    new ModuleProviderImpl(lateLogger, parameters, config, roles)
  }

  protected def loadRoles(parameters: RoleAppArguments, lateLogger: IzLogger, plugins: AllLoadedPlugins): RolesInfo = {
    val activeRoleNames = parameters.roles.map(_.role).toSet
    val mp = MirrorProvider.Impl
    val roleProvider: RoleProvider = new RoleProviderImpl(lateLogger, activeRoleNames, mp)
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






