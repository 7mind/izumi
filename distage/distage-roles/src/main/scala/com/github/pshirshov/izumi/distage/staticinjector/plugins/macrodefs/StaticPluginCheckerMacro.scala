package com.github.pshirshov.izumi.distage.staticinjector.plugins.macrodefs

import com.github.pshirshov.izumi.distage.bootstrap.BootstrapLocator
import com.github.pshirshov.izumi.distage.config.annotations.AbstractConfId
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.{ConfigModule, ConfigReferenceExtractor}
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.FactoryExecutor
import com.github.pshirshov.izumi.distage.plugins.PluginBase
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.staticinjector.plugins.ModuleRequirements
import com.typesafe.config.ConfigFactory
import distage.{BootstrapModuleDef, DIKey, Injector, Module, ModuleBase, OrderedPlan}
import io.github.classgraph.ClassGraph

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{u => ru}
import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox
import scala.reflect.runtime.currentMirror

object StaticPluginCheckerMacro {

  def implDefault[T <: PluginBase: c.WeakTypeTag, R <: ModuleRequirements: c.WeakTypeTag](c: blackbox.Context)(disableTags: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._

    implWithPluginConfig[T, R](c)(c.Expr[String](q"${""}"), disableTags, c.Expr[String](q"${""}"))
  }

  def implWithConfig[T <: PluginBase: c.WeakTypeTag, R <: ModuleRequirements: c.WeakTypeTag](c: blackbox.Context)(disableTags: c.Expr[String], configFileRegex: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._

    implWithPluginConfig[T, R](c)(c.Expr[String](q"${""}"), disableTags, configFileRegex)
  }

  def implWithPlugin[T <: PluginBase: c.WeakTypeTag, R <: ModuleRequirements: c.WeakTypeTag](c: blackbox.Context)(pluginPath: c.Expr[String], disableTags: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._

    implWithPluginConfig[T, R](c)(pluginPath, disableTags, c.Expr[String](q"${""}"))
  }

  def implWithPluginConfig[T <: PluginBase: c.WeakTypeTag, R <: ModuleRequirements: c.WeakTypeTag](c: blackbox.Context)(pluginPath: c.Expr[String], disableTags: c.Expr[String], configFileRegex: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._

    StaticPluginCheckerMacro.check(c)(
      pluginPath
      , c.Expr[String](q"${weakTypeOf[T].typeSymbol.asClass.fullName}")
      , c.Expr[String](q"${weakTypeOf[R].typeSymbol.asClass.fullName}")
      , disableTags
      , configFileRegex
    )
  }

  def check(c: blackbox.Context)
           ( pluginsPackage: c.Expr[String]
           , gcRoot: c.Expr[String]
           , requirements: c.Expr[String]
           , disabledTags: c.Expr[String]
           , configFileRegex: c.Expr[String]
           ): c.Expr[Unit] = {

    val abort = c.abort(c.enclosingPosition, _: String): Unit

    val pluginPath = stringLiteral(c)(c.universe)(pluginsPackage.tree)

    val loadedPlugins = if (pluginPath == "") {
      Seq.empty
    } else {
      val pluginLoader = new PluginLoaderDefaultImpl(PluginConfig(
        debug = false
        , packagesEnabled = Seq(pluginPath)
        , packagesDisabled = Seq.empty
      ))

      pluginLoader.load()
    }

    val configRegex = stringLiteral(c)(c.universe)(configFileRegex.tree)

    val configModule = if (configRegex == "") {
      None
    } else {
      val scanResult = new ClassGraph().scan()
      val configUrls = try {
        val resourceList = scanResult.getResourcesMatchingPattern(configRegex.r.pattern)
        try {
          resourceList.getURLs.asScala.toList
        } finally resourceList.close()
      } finally scanResult.close()

      val referenceConfig = configUrls.foldLeft(ConfigFactory.empty())(_ withFallback ConfigFactory.parseURL(_)).resolve()

      Some(new ConfigModule(AppConfig(referenceConfig)))
    }

    val gcRootPath = stringLiteral(c)(c.universe)(gcRoot.tree)

    val gcRootModule = if (gcRootPath == "") {
      None
    } else {
      Some(constructClass[PluginBase](gcRootPath, abort))
    }

    val requirementsPath = stringLiteral(c)(c.universe)(requirements.tree)

    val requirementsModule = if (requirementsPath == "") {
      None
    } else {
      Some(constructClass[ModuleRequirements](requirementsPath, abort))
    }

    val disableTags = stringLiteral(c)(c.universe)(disabledTags.tree).split(',').toSet

    check(loadedPlugins, configModule, additional = Module.empty, gcRootModule, requirementsModule, disableTags, abort = abort)

    c.universe.reify(())
  }

  def check(
             loadedPlugins: Seq[PluginBase]
           , configModule: Option[ConfigModule]
           , additional: ModuleBase
           , root: Option[ModuleBase]
           , moduleRequirements: Option[ModuleRequirements]
           , disabledTags: Set[String]
           , abort: String => Unit
           ): Unit = {

    val module = new ConfigurablePluginMergeStrategy(PluginMergeConfig(BindingTag.Expressions.Or(disabledTags.map(BindingTag.apply).map(BindingTag.Expressions.Has))))
      .merge(loadedPlugins :+ additional.morph[PluginBase] :+ root.toList.merge.morph[PluginBase])

    // If configModule is defined - check config, otherwise skip config keys
    val config = configModule.getOrElse(new BootstrapModuleDef {
      many[PlanningHook]
        .add[ConfigReferenceExtractor]
    })

    val bootstrap = new BootstrapLocator(BootstrapLocator.noReflectionBootstrap overridenBy config)
    val injector = Injector.inherit(bootstrap)

    val finalPlan = injector.plan(PlannerInput(module, root.fold(Set.empty[DIKey])(_.keys))).locateImports(bootstrap)
    val imports = finalPlan.unresolvedImports.left.getOrElse(Seq.empty).filter {
      case i if moduleRequirements.fold(false)(_.requiredKeys contains i.target) => false
      case _ => true
    }

    if (imports.nonEmpty)
      abort(
        s"""Plugin is incomplete!
           |
           |  ERROR: Missing imports:
           |    ${imports.mkString("\n    ")}
           |
           |  Module requirements were:
           |    ${moduleRequirements.fold(Set.empty[DIKey])(_.requiredKeys).mkString("\n    ")}
           |
           |  Plan was:
           |${finalPlan.render()}
           |
           |  ${configModule.fold("")(_ => s"Config was:\n  ${bootstrap.find[AppConfig].map(_.config)}")}
           |
           |  ${if (loadedPlugins.nonEmpty) s"Plugin classes were: ${loadedPlugins.map(_.getClass).mkString("\n    ")}" else ""}
           |    """.stripMargin
      )
  }

  private[this] def constructClass[T: ClassTag: ru.TypeTag](path: String, abort: String => Unit): T = {
    val mirror: ru.Mirror = currentMirror

    val clazz = mirror.staticClass(path)
    val tpe = clazz.toType
    val expectTpe = ru.typeOf[T]
    if (!(tpe weak_<:< expectTpe)) {
      abort(s"""Can't construct a value of `$expectTpe` from class found at "$path" - its class `$tpe` is NOT a subtype of `$expectTpe`!""")
    }

    implicitly[ClassTag[T]].runtimeClass.cast {
      mirror.reflectClass(clazz)
        .reflectConstructor(tpe.decls.collectFirst { case m: ru.MethodSymbol@unchecked if m.isPrimaryConstructor => m }.get)
        .apply()
    }.asInstanceOf[T]
  }

  private[this] def stringLiteral(c: blackbox.Context)(u: Universe)(tree: u.Tree): String =
    tree.collect {
      case l: u.Literal @unchecked if l.value.value.isInstanceOf[String] => l.value.value.asInstanceOf[String] // avoid unchecked warning
    }.headOption.getOrElse(c.abort(c.enclosingPosition, "must use string literal"))

  // FIXME: move to distage-model
  // blockers: AbstractConfId
  implicit final class OrderedPlanCheck(private val plan: OrderedPlan) {

    /**
      * @return this plan or a list of unsatisfied imports
      */
    def unresolvedImports: Either[Seq[ImportDependency], OrderedPlan] = {
      val nonMagicImports = plan.getImports.filter {
        // a hack to not account for distage-config *bootstrap module*
        // fixme: better scheme
        case ImportDependency(DIKey.IdKey(_, _: AbstractConfId), _, _) => false
        case i if i.target == DIKey.get[FactoryExecutor] => false
        case i if i.target == DIKey.get[LocatorRef] => false
        case _ => true
      }
      if (nonMagicImports.isEmpty) Right(plan) else Left(nonMagicImports)
    }
  }

}
