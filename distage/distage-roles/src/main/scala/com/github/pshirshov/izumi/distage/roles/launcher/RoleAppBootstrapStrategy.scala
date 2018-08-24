package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage
import com.github.pshirshov.izumi.distage.app.{ApplicationBootstrapStrategyBaseImpl, BootstrapContext, OpinionatedDiApp}
import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.codec.RuntimeConfigReaderDefaultImpl
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.planning.gc.TracingGcModule
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.distage.roles.impl.{LogInstances, RoleAppBootstrapStrategyArgs}
import com.github.pshirshov.izumi.distage.roles.roles
import com.github.pshirshov.izumi.distage.roles.roles.{RoleComponent, RoleService, RolesInfo}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.rendering.json.LogstageCirceRenderingPolicy
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceStaticImpl, StaticLogRouter}
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class RoleAppBootstrapStrategy[CommandlineConfig](
                                                   roleAppBootstrapStrategyArgs: RoleAppBootstrapStrategyArgs
                                                   , bsContext: BootstrapContext[CommandlineConfig]
                                                 ) extends ApplicationBootstrapStrategyBaseImpl(bsContext) {

  import roleAppBootstrapStrategyArgs._

  private val logger = new IzLogger(router(), CustomContext.empty)

  private val roleProvider: RoleProvider = new RoleProviderImpl(roleSet)

  def init(): RoleAppBootstrapStrategy[CommandlineConfig] = {
    showDepData(logger, "Application is about to start", this.getClass)
    using.foreach { u => showDepData(logger, s"... using ${u.libraryName}", u.clazz) }
    showDepData(logger, "... using izumi-r2", classOf[OpinionatedDiApp])
    this
  }

  protected val roleInfo = new AtomicReference[roles.RolesInfo]()

  override def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[BootstrapModuleDef] = {
    Quirks.discard(bs)

    logger.info(s"Loaded ${app.definition.bindings.size -> "app bindings"} and ${bs.definition.bindings.size -> "bootstrap bindings"}...")

    val roles = roleInfo.get()

    val servicesHook = new AssignableFromAutoSetHook[RoleService]()
    val closeablesHook = new AssignableFromAutoSetHook[AutoCloseable]()
    val componentsHook = new AssignableFromAutoSetHook[RoleComponent]()

    Seq(
      new ConfigModule(bsContext.appConfig)
      , new BootstrapModuleDef {
        many[PlanningHook]
          .add(servicesHook)
          .add(closeablesHook)
          .add(componentsHook)

        make[distage.roles.roles.RolesInfo].from(roles)
      }
      , new TracingGcModule(roles.requiredComponents)
    )
  }

  override def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy[LoadedPlugins] = {
    val bindings = app.flatMap(_.bindings)
    logger.info(s"Available ${app.size -> "app plugins"} and ${bs.size -> "bootstrap plugins"} and ${bindings.size -> "app bindings"}...")
    val roles: distage.roles.roles.RolesInfo = roleProvider.getInfo(bindings)
    roleInfo.set(roles) // TODO: mutable logic isn't so pretty. We need to maintain an immutable context somehow
    printRoleInfo(roles)

    val allDisabledTags = TagExpr.Strings.Or(disabledTags, roles.unrequiredRoleNames.toSeq.map(TagExpr.Strings.Has.apply): _*)
    logger.info(s"Disabled ${allDisabledTags -> "tags"}")

    new ConfigurablePluginMergeStrategy(PluginMergeConfig(
      allDisabledTags
      , Set.empty
      , Set.empty
      , Map.empty
    ))
  }

  private def printRoleInfo(roles: RolesInfo): Unit = {
    val availableRoleInfo = roles.availableRoleBindings.map {
      r =>
        s"${r.anno.headOption.getOrElse("N/A")}, ${r.tpe}, source=${r.source.getOrElse("N/A")}"
    }.sorted
    logger.info(s"Available ${availableRoleInfo.mkString("\n - ", "\n - ", "") -> "roles"}")
    logger.info(s"Requested ${roles.requiredRoleBindings.flatMap(_.anno).mkString("\n - ", "\n - ", "") -> "roles"}")
  }

  override def appModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[ModuleBase] = {
    Quirks.discard(bs, app)
    val baseMod = new ModuleDef {
      many[RoleService]
      many[RoleComponent]
      many[AutoCloseable]

      make[CustomContext].from(CustomContext.empty)
      make[IzLogger]
      make[RoleStarter]
    }
    Seq(baseMod overridenBy addOverrides)
  }


  // TODO: this is a temporary solution until we finish full-scale logger configuration support
  private def makeLogRouter(params: RoleAppBootstrapStrategyArgs): LogRouter = {
    import RoleAppBootstrapStrategy._
    import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

    // TODO: copypaste from di boostrap, this MUST disappear
    val symbolIntrospector = new SymbolIntrospectorDefaultImpl.Runtime
    val reflectionProvider = new ReflectionProviderDefaultImpl.Runtime(
      new DependencyKeyProviderDefaultImpl.Runtime(symbolIntrospector)
      , symbolIntrospector
    )
    val reader = new RuntimeConfigReaderDefaultImpl(reflectionProvider, symbolIntrospector)


    val logconf = Try(bsContext.appConfig.config.getConfig("logger")) match {
      case Failure(exception) =>
        System.err.println(s"`logger` section isn't defined in the config, logger isn't going to be configured: ${exception.getMessage}")
        SinksConfig(Map.empty, RenderingOptions(), json = false, None)

      case Success(value) =>
        reader.readConfig(value, SafeType.get[SinksConfig]).asInstanceOf[SinksConfig]
    }

    val renderingPolicy = if (logconf.json || params.jsonLogging) {
      new LogstageCirceRenderingPolicy()
    } else {
      new StringRenderingPolicy(logconf.options, logconf.layout)
    }

    val sinks = Seq(new ConsoleSink(renderingPolicy))

    val levels = logconf.levels.flatMap {
      case (stringLevel, pack) =>
        val level = LogInstances.toLevel(stringLevel)
        pack.map((_, LoggerConfig(level, sinks)))
    }

    // TODO: here we may read log configuration from config file
    val result = new ConfigurableLogRouter(
      new LogConfigServiceStaticImpl(
        levels
        , LoggerConfig(params.rootLogLevel, sinks)
      )
    )
    StaticLogRouter.instance.setup(result)
    result
  }

  override def router(): LogRouter = {
    makeLogRouter(roleAppBootstrapStrategyArgs)
  }

  // there are no bootstrap plugins in Izumi, no need to scan
  override def mkBootstrapLoader(): PluginLoader = () => Seq.empty

  private def showDepData(logger: IzLogger, msg: String, clazz: Class[_]): Unit = {
    val mf = IzManifest.manifest()(ClassTag(clazz)).map(IzManifest.read)
    val details = mf.getOrElse("{No version data}")

    logger.info(s"$msg : $details")
  }
}

object RoleAppBootstrapStrategy {

  final case class Using(libraryName: String, clazz: Class[_])

  case class SinksConfig(levels: Map[String, List[String]], options: RenderingOptions, json: Boolean, layout: Option[String])

}

