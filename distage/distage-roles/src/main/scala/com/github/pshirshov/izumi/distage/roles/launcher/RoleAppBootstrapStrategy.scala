package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.app.{ApplicationBootstrapStrategy, BootstrapConfig, OpinionatedDiApp}
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.{ConfigModule, ResolvedConfig, SimpleLoggerConfigurator}
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.planning.AutoSetModule
import com.github.pshirshov.izumi.distage.planning.extensions.GraphDumpBootstrapModule
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.impl.RoleAppBootstrapStrategyArgs
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.typesafe.config.{Config, ConfigFactory}
import distage.DIKey

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}





@deprecated("Migrate to new role infra", "2019-04-19")
class RoleAppBootstrapStrategy(
                                params: RoleAppBootstrapStrategyArgs
                              , override val context: BootstrapConfig
                              ) extends ApplicationBootstrapStrategy {

  import params._

  private val logger = IzLogger(params.rootLogLevel)

  private val mp = MirrorProvider.Impl
  private val roleProvider: RoleProvider[Identity] = new RoleProviderImpl(logger, roleSet, mp)

  def init(): this.type = {
    showDepData(logger, "Application is about to start", this.getClass)
    using.foreach { u => showDepData(logger, s"... using ${u.libraryName}", u.clazz) }
    showDepData(logger, "... using izumi-r2", classOf[OpinionatedDiApp])
    this
  }

  protected val roleInfo = new AtomicReference[RolesInfo]()

  protected lazy val config: AppConfig = buildConfig()

  protected def buildConfig(): AppConfig = {
    val commonConfigFile = params.primaryConfig
      .fold(ConfigSource.Resource("common-reference.conf"): ConfigSource)(f => ConfigSource.File(f))

    val roleConfigFiles = params.roleSet.toList.map {
      roleName =>
        params.roleConfigs.get(roleName).fold(ConfigSource.Resource(s"$roleName-reference.conf"): ConfigSource)(f => ConfigSource.File(f))
    }

    val allConfigs = roleConfigFiles :+ commonConfigFile

    logger.info(s"Using ${allConfigs.niceList() -> "config files"}")
    logger.info(s"Using system properties")

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

    val folded = foldConfigs(good.collect({ case (_, Success(c)) => c }))

    val config = ConfigFactory.systemProperties()
      .withFallback(folded)
      .resolve()

    AppConfig(config)
  }

  protected def foldConfigs(roleConfigs: Seq[Config]): Config = {
    roleConfigs.foldLeft(ConfigFactory.empty()) {
      case (acc, cfg) =>
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

  override def bootstrapModules(bs: MergedPlugins, app: MergedPlugins): Seq[BootstrapModuleDef] = {

    logger.info(s"Loaded ${app.definition.bindings.size -> "app bindings"} and ${bs.definition.bindings.size -> "bootstrap bindings"}...")

    val roles = roleInfo.get()

    val rolesModule = new BootstrapModuleDef {
      make[RolesInfo].from(roles)
    }
    val autosetModule = RoleAppBootstrapStrategy.roleAutoSetModule
    val configModule = new ConfigModule(config)
    val maybeDumpGraphModule = if (dumpContext) {
      Seq(new GraphDumpBootstrapModule())
    } else {
      Seq.empty
    }

    Seq(
      configModule,
      autosetModule,
      rolesModule,
    ) ++
      maybeDumpGraphModule
  }

  override def appModules(bs: MergedPlugins, app: MergedPlugins): Seq[Module] = {
    bs.discard()
    app.discard()

    val baseMod = new ModuleDef {
      make[CustomContext].from(CustomContext.empty)
      make[IzLogger]
      make[ComponentsLifecycleManager].from[ComponentsLifecycleManagerImpl]
      make[RoleStarter].from[RoleStarterImpl]
    }
    Seq(baseMod overridenBy addOverrides)
  }

  override def gcRoots(bs: MergedPlugins, app: MergedPlugins): Set[DIKey] = {
    (bs, app).discard()

    val roles = roleInfo.get()

    roles.requiredComponents ++ Set(
      RuntimeDIUniverse.DIKey.get[ResolvedConfig],
      RuntimeDIUniverse.DIKey.get[RoleStarter],
    )
  }

  override def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy = {

    val bindings = app.flatMap(_.bindings)
    logger.info(s"Available ${app.size -> "app plugins"} and ${bs.size -> "bootstrap plugins"} and ${bindings.size -> "app bindings"}...")

    // extract
    val roles: RolesInfo = roleProvider.getInfo(bindings)
    roleInfo.set(roles) // TODO: mutable logic isn't so pretty. We need to maintain an immutable context somehow
    printRoleInfo(roles)

    val unrequiredRoleTags = roles.unrequiredRoleNames.map(v => BindingTag.Expressions.Has(BindingTag(v)): BindingTag.Expressions.Expr)
    //
    val allDisabledTags = BindingTag.Expressions.Or(Set(disabledTags) ++ unrequiredRoleTags)
    logger.trace(s"Raw disabled tags ${allDisabledTags -> "expression"}")
    logger.info(s"Disabled ${BindingTag.Expressions.TagDNF.toDNF(allDisabledTags) -> "tags"}")

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
        s"${r.name}, ${r.tpe}, source=${r.source.getOrElse("N/A")}"
    }.sorted
    logger.info(s"Available ${availableRoleInfo.mkString("\n - ", "\n - ", "") -> "roles"}")
    logger.info(s"Requested ${roles.requiredRoleBindings.map(_.name).mkString("\n - ", "\n - ", "") -> "roles"}")
  }



  private[this] lazy val _router = {
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

  final val roleAutoSetModule = AutoSetModule()
    .register[RoleService]
    .register[AutoCloseable]
    .register[RoleComponent]
    .register[IntegrationCheck]
    .register[ExecutorService]
}
