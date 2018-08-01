package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.app.{ApplicationBootstrapStrategyBaseImpl, OpinionatedDiApp}
import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.planning.gc.TracingGcModule
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.launcher.RoleLauncherArgs.WriteReference
import com.github.pshirshov.izumi.distage.roles.roles.{PluginTags, RoleAppComponent, RoleAppService}
import com.github.pshirshov.izumi.fundamentals.collections.TagExpr
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.rendering.json.JsonRenderingPolicy
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceStaticImpl}
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink

import scala.reflect.ClassTag

class RoleAppBootstrapStrategy(params: RoleLauncherArgs, bsContext: RoleApp#BootstrapContext)
  extends ApplicationBootstrapStrategyBaseImpl(bsContext) {

  private val disabledTags = if (params.dummyStorage.contains(true)) {
    TagExpr.Strings.all(PluginTags.Production, PluginTags.Storage)
  } else {
    TagExpr.Strings.any(PluginTags.Test, PluginTags.Dummy)
  }

  private val roleSet = {
    if (params.writeReference.isDefined) {
      params.roles.map(_.name).toSet + ConfigWriter.id
    } else {
      params.roles.map(_.name).toSet
    }
  }

  private val logger = new IzLogger(router(), CustomContext.empty)

  private val roleProvider: RoleProvider = new RoleProviderImpl(roleSet)

  def init(): RoleAppBootstrapStrategy = {
    showDepData(logger, "Application is about to start", this.getClass)
    showDepData(logger, "... using tg-unified-sdk", classOf[RoleApp])
    showDepData(logger, "... using izumi-r2", classOf[OpinionatedDiApp])
    this
  }

  protected val roleInfo = new AtomicReference[RoleInfo]()

  override def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[ModuleBase] = {
    Quirks.discard(bs)

    logger.info(s"Loaded ${app.definition.bindings.size -> "app bindings"} and ${bs.definition.bindings.size -> "bootstrap bindings"}...")

    val roles = roleInfo.get()

    val servicesHook = new AssignableFromAutoSetHook[RoleAppService]()
    val closeablesHook = new AssignableFromAutoSetHook[AutoCloseable]()
    val componentsHook = new AssignableFromAutoSetHook[RoleAppComponent]()

    Seq(
      new ConfigModule(bsContext.appConfig)
      , new ModuleDef {


        many[PlanningHook]
          .add(servicesHook)
          .add(closeablesHook)
          .add(componentsHook)

        make[RoleInfo].from(roles)
      }
      , new TracingGcModule(roles.requiredComponents)
    )
  }

  override def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy[LoadedPlugins] = {
    val bindings = app.flatMap(_.bindings)
    logger.info(s"Available ${app.size -> "app plugins"} and ${bs.size -> "bootstrap plugins"} and ${bindings.size -> "app bindings"}...")
    val roles = roleProvider.getInfo(bindings)
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

  private def printRoleInfo(roles: RoleInfo): Unit = {
    val availableRoleInfo = roles.availableRoleBindings.map {
      r =>
        s"${r.anno.headOption.getOrElse("N/A")}, ${r.tpe}, source=${r.source.getOrElse("N/A")}"
    }.sorted
    logger.info(s"Available ${availableRoleInfo.mkString("\n - ", "\n - ", "") -> "roles"}")
    logger.info(s"Requested ${roles.requiredRoleBindings.flatMap(_.anno).mkString("\n - ", "\n - ", "") -> "roles"}")
  }

  override def appModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[ModuleBase] = {
    Quirks.discard(bs, app)
    Seq(new ModuleDef {
      many[RoleAppService]
      many[RoleAppComponent]
      many[AutoCloseable]

      make[WriteReference].from(params.writeReference.getOrElse(WriteReference()))
      make[CustomContext].from(CustomContext.empty)
      make[IzLogger]
      make[RoleStarter]
    })
  }

  private def makeLogRouter(params: RoleLauncherArgs): LogRouter = {
    val renderingPolicy = if (params.jsonLogging.contains(true)) {
      new JsonRenderingPolicy()
    } else {
      new StringRenderingPolicy(RenderingOptions(withExceptions = true, withColors = true))
    }

    val sinks = Seq(new ConsoleSink(renderingPolicy))

    // TODO: here we may read log configuration from config file
    new ConfigurableLogRouter(
      new LogConfigServiceStaticImpl(Map.empty, LoggerConfig(params.rootLogLevel, sinks))
    )
  }

  override def router(): LogRouter = {
    makeLogRouter(params)
  }

  // there are no bootstrap plugins in Izumi, no need to scan
  override def mkBootstrapLoader(): PluginLoader = () => Seq.empty

  private def showDepData(logger: IzLogger, msg: String, clazz: Class[_]): Unit = {
    val mf = IzManifest.manifest()(ClassTag(clazz)).map(IzManifest.read)
    val details = mf.getOrElse("{No version data}")

    logger.info(s"$msg : $details")
  }
}
