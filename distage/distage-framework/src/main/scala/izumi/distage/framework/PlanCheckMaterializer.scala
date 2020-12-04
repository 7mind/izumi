package izumi.distage.framework

import izumi.distage.framework.PlanCheck.PlanCheckResult
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.StaticPluginLoader.StaticPluginLoaderMacro
import izumi.distage.roles.PlanHolder
import izumi.fundamentals.reflection.{TrivialMacroLogger, TypeUtil}

import scala.language.experimental.macros
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox
import scala.reflect.runtime.{universe => ru}

/**
  * This implicit performs a compile-time check for a given `distage`-based `App` when materialized.
  *
  * The reason to use an implicit macro for this, instead of a direct def-macro is because
  * implicit macros are more compositional – you may write new functions using the implicit without writing new macros,
  * by contrast macros can only be composed inside new macros – and this in turn requires separating them into different
  * `sbt` modules.
  *
  * @tparam AppMain The application to check, this should be a static object, most often a `main` object inherited from [[izumi.distage.roles.RoleAppMain]]
  * @tparam Cfg Additional configuration options for compile-time checker
  *
  * @see [[izumi.distage.framework.PlanCheck User API]]
  * @see [[izumi.distage.framework.PlanCheckConfig Configuration Options]]
  */
final case class PlanCheckMaterializer[AppMain <: PlanHolder, -Cfg <: PlanCheckConfig.Any](
  app: AppMain,
  roles: String,
  excludeActivations: String,
  config: String,
  checkConfig: Option[Boolean],
  onlyWarn: Option[Boolean],
  printBindings: Option[Boolean],
  checkPassed: Boolean,
  checkedPlugins: Seq[PluginBase],
) {
  def checkAtRuntime(): PlanCheckResult =
    PlanCheck.runtime.checkApp(
      app = app,
      roles = roles,
      excludeActivations = excludeActivations,
      config = config,
      checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
      printBindings = printBindings.getOrElse(PlanCheck.defaultPrintBindings),
    )
}

object PlanCheckMaterializer {

  implicit def materialize[
    AppMain <: PlanHolder,
    Roles <: String,
    ExcludeActivations <: String,
    Config <: String,
    CheckConfig <: Boolean,
    OnlyWarn <: Boolean,
    PrintBindings <: Boolean,
  ]: PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn, PrintBindings]] =
    macro PlanCheckMaterializerMacro.impl[AppMain, Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn, PrintBindings]

  object PlanCheckMaterializerMacro {
    private[this] final val sysPropOnlyWarn = DebugProperties.`izumi.distage.plancheck.onlywarn`.boolValue(false)

    def impl[
      AppMain <: PlanHolder: c.WeakTypeTag,
      Roles <: String: c.WeakTypeTag,
      Activations <: String: c.WeakTypeTag,
      Config <: String: c.WeakTypeTag,
      CheckConfig <: Boolean: c.WeakTypeTag,
      OnlyWarn <: Boolean: c.WeakTypeTag,
      PrintBindings <: Boolean: c.WeakTypeTag,
    ](c: blackbox.Context
    ): c.Expr[PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, Activations, Config, CheckConfig, OnlyWarn, PrintBindings]]] = {
      import c.universe._

      def getConstantType0[S](tpe: Type): S = {
        tpe match {
          case ConstantType(Constant(s)) => s.asInstanceOf[S]
          case tpe =>
            c.abort(
              c.enclosingPosition,
              s"""When materializing ${weakTypeOf[PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, Activations, Config, CheckConfig, OnlyWarn, PrintBindings]]]},
                 |Bad constant type: $tpe - Not a constant! Only constant literal types are supported!
               """.stripMargin,
            )
        }
      }
      def getConstantType[S: c.WeakTypeTag]: S = {
        getConstantType0(weakTypeOf[S].dealias)
      }
      def noneIfUnset[S: c.WeakTypeTag]: Option[S] = {
        val tpe = weakTypeOf[S].dealias
        if (tpe =:= typeOf[PlanCheckConfig.Unset]) {
          None
        } else {
          Some(getConstantType0[S](tpe))
        }
      }

      val roleAppMain = c.Expr[AppMain](q"${weakTypeOf[AppMain].asInstanceOf[SingleTypeApi].sym.asTerm}")
      val roles = getConstantType[Roles]
      val activations = getConstantType[Activations]
      val config = getConstantType[Config]
      val checkConfig = noneIfUnset[CheckConfig]
      val onlyWarn = noneIfUnset[OnlyWarn]
      val printBindings = noneIfUnset[PrintBindings]

      val maybeMain = instantiateObject[AppMain](c.universe)

      val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.plancheck`.name)
      val (checkPassed, checkedLoadedPlugins) =
        PlanCheck.runtime.checkApp(
          app = maybeMain,
          roles = roles,
          excludeActivations = activations,
          config = config,
          checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
          printBindings = printBindings.getOrElse(PlanCheck.defaultPrintBindings),
          logger = logger,
        ) match {
          case PlanCheckResult.Correct(loadedPlugins) =>
            true -> loadedPlugins
          case PlanCheckResult.Incorrect(loadedPlugins, message, _) =>
            val warn = onlyWarn.getOrElse(sysPropOnlyWarn)
            if (warn) {
              c.warning(c.enclosingPosition, message)
              false -> loadedPlugins
            } else {
              c.abort(c.enclosingPosition, message)
            }
        }

      // filter out anonymous classes that can't be referred in code
      // & retain only those that are suitable for being loaded by PluginLoader (objects / zero-arg classes) -
      // and that can be easily instantiated with `new`
      val referencablePlugins = checkedLoadedPlugins.allRaw
        .filterNot(TypeUtil isAnonymous _.getClass)
        .filter(p => TypeUtil.isObject(p.getClass).isDefined || TypeUtil.isZeroArgClass(p.getClass).isDefined)

      // We _have_ to call `new` to cause Intellij's incremental compiler to recompile users,
      // we can't just splice a type reference or create an expression referencing the type for it to pickup,
      // Zinc does recompile in these cases, but for Intellij `new` is required
      val pluginsList: List[Tree] = StaticPluginLoaderMacro.instantiatePluginsInCode(c)(referencablePlugins)
      val referenceStmt = c.Expr[List[PluginBase]](Liftable.liftList[Tree].apply(pluginsList))
      val checkPassedExpr = c.Expr[Boolean](Liftable.liftBoolean(checkPassed))

      def lit[T: c.WeakTypeTag](s: T): c.Expr[T] = c.Expr[T](Literal(Constant(s)))
      def opt[T: c.WeakTypeTag](s: Option[T]): c.Expr[Option[T]] = {
        s match {
          case Some(value) => reify(Some[T](lit[T](value).splice))
          case None => reify(None)
        }
      }

      reify {
        val referencedPlugins = referenceStmt.splice
        new PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, Activations, Config, CheckConfig, OnlyWarn, PrintBindings]](
          roleAppMain.splice,
          lit[Roles](roles).splice,
          lit[Activations](activations).splice,
          lit[Config](config).splice,
          opt[CheckConfig](checkConfig).splice,
          opt[OnlyWarn](onlyWarn).splice,
          opt[PrintBindings](printBindings).splice,
          checkPassedExpr.splice,
          referencedPlugins,
        )
      }
    }

    private[this] def instantiateObject[T](u: Universe)(implicit tpe: u.WeakTypeTag[T]): T = {
      val symbol = tpe.tpe.erasure.typeSymbol
      if (symbol.owner.isPackage || symbol.owner.isPackageClass) {
        val className = s"${symbol.fullName}$$"
        val clazz = Class.forName(className)
        TypeUtil.instantiateObject[T](clazz)
      } else {
        ru.runtimeMirror(getClass.getClassLoader).reflectModule(symbol.asClass.module.asModule.asInstanceOf[ru.ModuleSymbol]).instance.asInstanceOf[T]
      }
    }
  }

}
