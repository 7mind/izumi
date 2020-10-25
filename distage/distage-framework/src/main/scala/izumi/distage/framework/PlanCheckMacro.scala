package izumi.distage.framework

import izumi.distage.framework.PlanCheck.PlanCheckResult
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.StaticPluginLoader.StaticPluginLoaderMacro
import izumi.distage.roles.PlanHolder
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.reflection.{TrivialMacroLogger, TypeUtil}

import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

object PlanCheckMacro {
  private[this] final val sysPropOnlyWarn = DebugProperties.`izumi.distage.plancheck.onlywarn`.boolValue(false)

  def impl[
    RoleAppMain <: PlanHolder: c.WeakTypeTag,
    Roles <: String: c.WeakTypeTag,
    Activations <: String: c.WeakTypeTag,
    Config <: String: c.WeakTypeTag,
    CheckConfig <: Boolean: c.WeakTypeTag,
    OnlyWarn <: Boolean: c.WeakTypeTag,
  ](c: blackbox.Context
  ): c.Expr[PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, OnlyWarn]] = {
    import c.universe._

    def getConstantType0[S](tpe: Type): S = {
      tpe match {
        case ConstantType(Constant(s)) => s.asInstanceOf[S]
        case tpe =>
          c.abort(
            c.enclosingPosition,
            s"""When materializing ${weakTypeOf[PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, OnlyWarn]]},
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
      if (tpe =:= typeOf[PerformPlanCheck.Unset]) {
        None
      } else {
        Some(getConstantType0[S](tpe))
      }
    }

    val roleAppMain = c.Expr[RoleAppMain](q"${weakTypeOf[RoleAppMain].asInstanceOf[SingleTypeApi].sym.asTerm}")
    val roles = getConstantType[Roles]
    val activations = getConstantType[Activations]
    val config = getConstantType[Config]
    val checkConfig = noneIfUnset[CheckConfig]
    val onlyWarn = noneIfUnset[OnlyWarn]

    val maybeMain = instantiateObject[RoleAppMain](c.universe)

    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.plancheck`.name)
    val checkedLoadedPlugins =
      PlanCheck.checkRoleApp(
        roleAppMain = maybeMain,
        roles = roles,
        excludeActivations = activations,
        config = config,
        checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
        logger = logger,
      ) match {
        case PlanCheckResult.Correct(loadedPlugins) =>
          loadedPlugins
        case PlanCheckResult.Incorrect(loadedPlugins, exception) =>
          val warn = onlyWarn.getOrElse(sysPropOnlyWarn)
          if (warn) {
            c.warning(c.enclosingPosition, exception.stackTrace)
            loadedPlugins
          } else {
            c.abort(c.enclosingPosition, exception.stackTrace)
          }
      }

    // filter out anonymous classes that can't be referred in code
    // & retain only those that are suitable for being loaded by PluginLoader (objects / zero-arg classes) -
    // and that can be easily instantiated with `new`
    val referencablePlugins = checkedLoadedPlugins
      .allRaw
      .filterNot(TypeUtil isAnonymous _.getClass)
      .filter(p => TypeUtil.isObject(p.getClass).isDefined || TypeUtil.isZeroArgClass(p.getClass).isDefined)

    // We _have_ to call `new` to cause Intellij's incremental compiler to recompile users,
    // we can't just splice a type reference or create an expression referencing the type for it to pickup,
    // Zinc does recompile in these cases, but for Intellij `new` is required
    val pluginsList: List[Tree] = StaticPluginLoaderMacro.instantiatePluginsInCode(c)(referencablePlugins)
    val referenceStmt = c.Expr[List[PluginBase]](Liftable.liftList[Tree].apply(pluginsList))

    def lit[T: c.WeakTypeTag](s: T): c.Expr[T] = c.Expr[T](Literal(Constant(s)))
    def opt[T: c.WeakTypeTag](s: Option[T]): c.Expr[Option[T]] = {
      s match {
        case Some(value) => reify(Some[T](lit[T](value).splice))
        case None => reify(None)
      }
    }

    reify {
      val plugins = referenceStmt.splice
      new PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, OnlyWarn](
        roleAppMain.splice,
        lit[Roles](roles).splice,
        lit[Activations](activations).splice,
        lit[Config](config).splice,
        opt[CheckConfig](checkConfig).splice,
        opt[OnlyWarn](onlyWarn).splice,
        plugins,
      )
    }
  }

  private[this] def instantiateObject[T](u: Universe)(implicit tpe: u.WeakTypeTag[T]): T = {
    val className = s"${tpe.tpe.erasure.typeSymbol.fullName}$$"
    val clazz = Class.forName(className)
    TypeUtil.instantiateObject[T](clazz)
  }

}
