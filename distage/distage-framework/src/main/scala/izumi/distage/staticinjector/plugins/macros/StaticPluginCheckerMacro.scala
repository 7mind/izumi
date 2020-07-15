package izumi.distage.staticinjector.plugins.macros

import com.typesafe.config.ConfigFactory
import distage._
import io.github.classgraph.ClassGraph
import izumi.distage.bootstrap.{BootstrapLocator, Cycles}
import izumi.distage.config.AppConfigModule
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ActivationInfoExtractor
import izumi.distage.model.PlannerInput
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.plugins.merge.SimplePluginMergeStrategy
import izumi.distage.plugins.{PluginBase, PluginConfig}
import izumi.distage.roles.services.RoleAppActivationParser
import izumi.distage.staticinjector.plugins.ModuleRequirements
import izumi.fundamentals.platform.strings.IzString._
import izumi.fundamentals.reflection.ReflectionUtil
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters._
import scala.reflect.macros.blackbox
import scala.reflect.runtime.{currentMirror, universe => ru}

object StaticPluginCheckerMacro {

  def implDefault[T <: ModuleBase: c.WeakTypeTag, R <: ModuleRequirements: c.WeakTypeTag](c: blackbox.Context)(activations: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._

    implWithPluginConfig[T, R](c)(c.Expr[String](q"${""}"), activations, c.Expr[String](q"${""}"))
  }

  def implWithConfig[T <: ModuleBase: c.WeakTypeTag, R <: ModuleRequirements: c.WeakTypeTag](
    c: blackbox.Context
  )(activations: c.Expr[String],
    configFileRegex: c.Expr[String],
  ): c.Expr[Unit] = {
    import c.universe._

    implWithPluginConfig[T, R](c)(c.Expr[String](q"${""}"), activations, configFileRegex)
  }

  def implWithPlugin[T <: ModuleBase: c.WeakTypeTag, R <: ModuleRequirements: c.WeakTypeTag](
    c: blackbox.Context
  )(pluginPath: c.Expr[String],
    activations: c.Expr[String],
  ): c.Expr[Unit] = {
    import c.universe._

    implWithPluginConfig[T, R](c)(pluginPath, activations, c.Expr[String](q"${""}"))
  }

  def implWithPluginConfig[T <: ModuleBase: c.WeakTypeTag, R <: ModuleRequirements: c.WeakTypeTag](
    c: blackbox.Context
  )(pluginPath: c.Expr[String],
    activations: c.Expr[String],
    configFileRegex: c.Expr[String],
  ): c.Expr[Unit] = {
    import c.universe._

    StaticPluginCheckerMacro.check(c)(
      pluginsPackage = pluginPath,
      gcRoot = c.Expr[String](q"${weakTypeOf[T].typeSymbol.asClass.fullName}"),
      requirements = c.Expr[String](q"${weakTypeOf[R].typeSymbol.asClass.fullName}"),
      activations = activations,
      configFileRegex = configFileRegex,
    )
  }

  def check(
    c: blackbox.Context
  )(pluginsPackage: c.Expr[String],
    gcRoot: c.Expr[String],
    requirements: c.Expr[String],
    activations: c.Expr[String],
    configFileRegex: c.Expr[String],
  ): c.Expr[Unit] = {

    val abort = c.abort(c.enclosingPosition, _: String): Unit

    val pluginPath = ReflectionUtil.getStringLiteral(c)(pluginsPackage.tree)

    val loadedPlugins = if (pluginPath == "") {
      Seq.empty
    } else {
      new PluginLoaderDefaultImpl().load(PluginConfig.packages(Seq(pluginPath)))
    }

    val configRegex = ReflectionUtil.getStringLiteral(c)(configFileRegex.tree)

    val configModule = if (configRegex == "") {
      None
    } else {
      val scanResult = new ClassGraph().scan()
      val configUrls =
        try {
          val resourceList = scanResult.getResourcesMatchingPattern(configRegex.r.pattern)
          try {
            resourceList.getURLs.asScala.toList
          } finally resourceList.close()
        } finally scanResult.close()

      val referenceConfig = configUrls.foldLeft(ConfigFactory.empty())(_ withFallback ConfigFactory.parseURL(_)).resolve()

      Some(new AppConfigModule(AppConfig(referenceConfig)))
    }

    val gcRootPath = ReflectionUtil.getStringLiteral(c)(gcRoot.tree)

    val gcRootModule: Option[ModuleBase] = if (gcRootPath == "") {
      None
    } else {
      Some(constructClass[ModuleBase](gcRootPath, abort))
    }

    val requirementsPath = ReflectionUtil.getStringLiteral(c)(requirements.tree)

    val requirementsModule = if (requirementsPath == "") {
      None
    } else {
      Some(constructClass[ModuleRequirements](requirementsPath, abort))
    }

    val activationsVals = ReflectionUtil.getStringLiteral(c)(activations.tree).split(',').toSeq.filter(_.nonEmpty)

    check(
      loadedPlugins = loadedPlugins,
      configModule = configModule,
      additional = Module.empty,
      root = gcRootModule,
      moduleRequirements = requirementsModule,
      activations = activationsVals,
      abort = abort,
    )

    c.universe.reify(())
  }

  def check(
    loadedPlugins: Seq[PluginBase],
    configModule: Option[AppConfigModule],
    additional: ModuleBase,
    root: Option[ModuleBase],
    moduleRequirements: Option[ModuleRequirements],
    activations: Seq[String],
    abort: String => Unit,
  ): Unit = {

    // If configModule is defined - check config
    val config = configModule.getOrElse(new ModuleDef {
      make[AppConfig].from(AppConfig(ConfigFactory.empty()))
    })

    val module = config overridenBy SimplePluginMergeStrategy.merge(loadedPlugins :+ additional.morph[PluginBase] :+ root.toList.merge.morph[PluginBase])

    val logger = IzLogger.NullLogger

    val bootstrap = new BootstrapLocator(BootstrapLocator.defaultBootstrap, Activation(Cycles -> Cycles.Proxy))
    val injector = Injector.inherit(bootstrap)

    val activation = {
      val activationInfo = ActivationInfoExtractor.findAvailableChoices(module)
      new RoleAppActivationParser.Impl(logger).parseActivation(activations.map(_.split2(':')), activationInfo)
    }

    val finalPlan = injector.plan(PlannerInput(module, activation, root.fold(Set.empty[DIKey])(_.keys))).locateImports(bootstrap)
    val imports = finalPlan.unresolvedImports.left.getOrElse(Seq.empty).filter {
      case i if moduleRequirements.fold(false)(_.requiredKeys contains i.target) => false
      case _ => true
    }
    import izumi.fundamentals.platform.strings.IzString._

    if (imports.nonEmpty)
      abort(
        s"""Plugin is incomplete!
           |
           |ERROR: Missing imports:
           |${imports.niceList()}
           |
           |Module requirements were:
           |${moduleRequirements.fold(Set.empty[DIKey])(_.requiredKeys).niceList()}
           |${if (loadedPlugins.nonEmpty) s"\nPlugin classes were: ${loadedPlugins.map(_.getClass).niceList()}" else ""}
           |
           |Plan was:
           |${finalPlan.render()}
           |
           |${configModule.fold("")(_ => s"Config was:\n${bootstrap.find[AppConfig].map(_.config).toString.shift(2)}")}""".stripMargin
      )
  }

  private[this] def constructClass[T: ru.TypeTag](path: String, abort: String => Unit): T = {
    val mirror: ru.Mirror = currentMirror

    val clazz = mirror.staticClass(path) match {
      case r if r.baseClasses == Nil =>
        mirror.staticModule(path).moduleClass.asClass
      case o => o
    }
    val tpe = clazz.toType
    val expectTpe = ru.typeOf[T]
    if (!(tpe weak_<:< expectTpe)) {
      abort(s"""Can't construct a value of `$expectTpe` from class found at "$path" - its class `$tpe` is NOT a subtype of `$expectTpe`!
               |baseClasses were: ${tpe.baseClasses}; symbol: $clazz
               |""".stripMargin)
    }

    if (clazz.isModuleClass) {
      mirror.reflectModule(clazz.thisPrefix.termSymbol.asModule).instance.asInstanceOf[T]
    } else {
      mirror
        .reflectClass(clazz).reflectConstructor {
          tpe
            .decls.collectFirst {
              case m: ru.MethodSymbol @unchecked if m.isPrimaryConstructor => m
            }.get
        }.apply().asInstanceOf[T]
    }
  }

}
