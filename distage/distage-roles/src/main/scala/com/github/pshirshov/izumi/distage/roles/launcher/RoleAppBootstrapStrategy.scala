package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage
import com.github.pshirshov.izumi.distage.app.{ApplicationBootstrapStrategyBaseImpl, BootstrapContext, OpinionatedDiApp}
import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.planning.gc.TracingGcModule
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.impl.RoleAppBootstrapStrategyArgs
import com.github.pshirshov.izumi.distage.roles.roles
import com.github.pshirshov.izumi.distage.roles.roles.{RoleComponent, RoleService, RolesInfo}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.typesafe.config.{Config, ConfigFactory}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


sealed trait ConfigSource {
}

object ConfigSource {
  final case class Resource(name: String) extends ConfigSource {
    override def toString: String = s"resource:$name"
  }
  final case class File(file: java.io.File) extends ConfigSource {
    override def toString: String = s"file:$file"
  }
}

class RoleAppBootstrapStrategy[CommandlineConfig](
                                                   params: RoleAppBootstrapStrategyArgs
                                                   , bsContext: BootstrapContext[CommandlineConfig]
                                                 ) extends ApplicationBootstrapStrategyBaseImpl(bsContext) {

  import params._

  private val logger = IzLogger.basic(params.rootLogLevel)

  private val roleProvider: RoleProvider = new RoleProviderImpl(roleSet)

  def init(): RoleAppBootstrapStrategy[CommandlineConfig] = {
    showDepData(logger, "Application is about to start", this.getClass)
    using.foreach { u => showDepData(logger, s"... using ${u.libraryName}", u.clazz) }
    showDepData(logger, "... using izumi-r2", classOf[OpinionatedDiApp])
    this
  }

  protected val roleInfo = new AtomicReference[roles.RolesInfo]()

  protected lazy val config: AppConfig = buildConfig()

  protected def buildConfig(): AppConfig = {
    val commonConfigFile = params.primaryConfig
      .fold(ConfigSource.Resource("common-reference.conf") : ConfigSource)(f => ConfigSource.File(f))

    val roleConfigFiles = params.roleSet.toList.map {
      roleName =>
        params.roleConfigs.get(roleName).fold(ConfigSource.Resource(s"$roleName-reference.conf") : ConfigSource)(f => ConfigSource.File(f))
    }

    val allConfigs = roleConfigFiles :+ commonConfigFile

    logger.info(s"Using ${allConfigs.niceList() -> "config files"}")

    val loaded = allConfigs.map {
      case s@ConfigSource.File(file) =>
        s -> Try(ConfigFactory.parseFile(file))

      case s@ConfigSource.Resource(name) =>
        s -> Try(ConfigFactory.parseResources(name))
    }

    val (good, bad) = loaded.partition(_._2.isSuccess)

    if (bad.nonEmpty) {
      val failures = bad.collect {
        case (s, Failure(f)) =>
          s"$s: $f"
      }

      logger.error(s"Failed to load configs: ${failures.niceList() -> "failures"}")
    }

    AppConfig(foldConfigs(ConfigFactory.defaultApplication(), good.collect({case (_, Success(c)) => c})))
  }

  protected def foldConfigs(appConfig: Config, roleConfigs: Seq[Config]): Config = {
    roleConfigs.foldRight(appConfig) {
      case (cfg, acc) =>
        verifyConfigs(cfg, acc)
        acc.withFallback(cfg)
    }
  }

  protected def verifyConfigs(cfg: Config, acc: Config): Unit = {
    val duplicateKeys = acc.entrySet().asScala.map(_.getKey).intersect(cfg.entrySet().asScala.map(_.getKey))
    if (duplicateKeys.nonEmpty) {
      logger.warn(s"Found duplicated keys in supplied configs: ${duplicateKeys.niceList() -> "keys" -> null}")
    }
  }

  protected def loadDefaultConfig: Boolean = {
    params.primaryConfig.isEmpty && params.roleConfigs.isEmpty
  }

  override def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[BootstrapModuleDef] = {
    Quirks.discard(bs)

    logger.info(s"Loaded ${app.definition.bindings.size -> "app bindings"} and ${bs.definition.bindings.size -> "bootstrap bindings"}...")

    val roles = roleInfo.get()

    val servicesHook = new AssignableFromAutoSetHook[RoleService]()
    val closeablesHook = new AssignableFromAutoSetHook[AutoCloseable]()
    val componentsHook = new AssignableFromAutoSetHook[RoleComponent]()

    Seq(
      new ConfigModule(config)
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

    val unrequiredRoleTags = roles.unrequiredRoleNames.map(v => TagExpr.Strings.Has(v): TagExpr.Strings.Expr)
    val allDisabledTags = TagExpr.Strings.Or(Set(disabledTags) ++ unrequiredRoleTags)
    logger.trace(s"Raw disabled tags ${allDisabledTags -> "expression"}")
    logger.info(s"Disabled ${TagExpr.Strings.TagDNF.toDNF(allDisabledTags) -> "tags"}")

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

  private lazy val _router = {
    new SimpleLoggerConfigurator(logger)
      .makeLogRouter(
        config.config.getConfig("logger")
        , params.rootLogLevel
        , params.jsonLogging
      )
  }

  override def router(): LogRouter = _router

  private def showDepData(logger: IzLogger, msg: String, clazz: Class[_]): Unit = {
    val mf = IzManifest.manifest()(ClassTag(clazz)).map(IzManifest.read)
    val details = mf.getOrElse("{No version data}")

    logger.info(s"$msg : $details")
  }
}

object RoleAppBootstrapStrategy {

  final case class Using(libraryName: String, clazz: Class[_])


}



