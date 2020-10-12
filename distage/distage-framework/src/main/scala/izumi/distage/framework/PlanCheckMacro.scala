package izumi.distage.framework

import izumi.distage.framework.PlanCheck.checkRoleApp
import izumi.distage.plugins.StaticPluginLoader.StaticPluginLoaderMacro
import izumi.distage.roles.PlanHolder
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.platform.language.LiteralCompat
import izumi.fundamentals.reflection.TypeUtil

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.api.Universe
import scala.reflect.macros.{blackbox, whitebox}

object PlanCheckMacro {

  final class PerformPlanCheck[T <: PlanHolder, Roles <: String, Activations <: String, Config <: String, CheckConfig <: Boolean](
    roleAppMain: T,
    roles: Roles,
    activations: Activations,
    config: Config,
    checkConfig: CheckConfig,
  ) {
    def run(): Unit = checkRoleApp(roleAppMain, roles, activations, config, checkConfig)
  }
  object PerformPlanCheck {
    implicit def performCompileTimeCheck[
      RoleAppMain <: PlanHolder,
      Roles <: String,
      Activations <: String,
      Config <: String,
      CheckConfig <: Boolean,
    ]: PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig] =
      macro Materialize.impl[RoleAppMain, Roles, Activations, Config, CheckConfig]
  }

  object Materialize {
    def impl[
      RoleAppMain <: PlanHolder: c.WeakTypeTag,
      Roles <: String: c.WeakTypeTag,
      Activations <: String: c.WeakTypeTag,
      Config <: String: c.WeakTypeTag,
      CheckConfig <: Boolean: c.WeakTypeTag,
    ](c: blackbox.Context
    ): c.Expr[PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig]] = {
      import c.universe._

      def getConstantType[S: c.WeakTypeTag]: S = {
        weakTypeOf[S].dealias match {
          case ConstantType(Constant(s)) => s.asInstanceOf[S]
          case tpe =>
            c.abort(
              c.enclosingPosition,
              s"""When materializing ${weakTypeOf[PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig]]},
                 |Bad String constant type: $tpe - Not a constant! Only constant string literal types are supported!
               """.stripMargin,
            )
        }
      }

      val roleAppMain = c.Expr[RoleAppMain](q"${weakTypeOf[RoleAppMain].asInstanceOf[SingleTypeApi].sym.asTerm}")
      val roles = getConstantType[Roles]
      val activations = getConstantType[Activations]
      val config = getConstantType[Config]
      val checkConfig = getConstantType[CheckConfig]

      val maybeMain = instantiateObject[RoleAppMain](c.universe)

      val checkedLoadedPlugins =
        try {
          PlanCheck.checkRoleApp(maybeMain, roles, activations, config)
        } catch {
          case t: Throwable =>
            c.abort(c.enclosingPosition, t.stackTrace)
        }

      // filter out anonymous classes that can't be referred in code
      // & retain only those that are suitable for being loaded by PluginLoader (objects / zero-arg classes) -
      // and that can be easily instantiated with `new`
      val referencablePlugins = checkedLoadedPlugins
        .allRaw
        .filterNot(_.getClass.isAnonymousClass)
        .filter(p => TypeUtil.isObject(p.getClass).isDefined || TypeUtil.isZeroArgClass(p.getClass).isDefined)

      // We _have_ to call `new` to cause Intellij's incremental compiler to recompile users,
      // we can't just splice a type reference or create an expression referencing the type for it to pickup,
      // Zinc does recompile in these cases, but for Intellij `new` is required
      val pluginsList: List[c.Tree] = StaticPluginLoaderMacro.instantiatePluginsInCode(c)(referencablePlugins)
      val referenceStmt = c.Expr[Unit](q"{ lazy val _ = $pluginsList ; () }")

      def lit[T: c.WeakTypeTag](s: T): c.Expr[T] = c.Expr[T](Literal(Constant(s)))

      reify {
        referenceStmt.splice
        new PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig](
          roleAppMain.splice,
          lit[Roles](roles).splice,
          lit[Activations](activations).splice,
          lit[Config](config).splice,
          lit[CheckConfig](checkConfig).splice,
        )
      }
    }
  }

  // 2.12 requires `Witness`
  class Impl[RoleAppMain <: PlanHolder, Roles <: LiteralString, Activations <: LiteralString, Config <: LiteralString, CheckConfig <: LiteralBoolean](
    roleAppMain: RoleAppMain,
    roles: Roles with LiteralString = LiteralString("*"),
    activations: Activations with LiteralString = LiteralString("*"),
    config: Config with LiteralString = LiteralString("*"),
    checkConfig: CheckConfig with LiteralBoolean = LiteralBoolean.True,
  )(implicit
    val planCheck: PerformPlanCheck[RoleAppMain, Roles#T, Activations#T, Config#T, CheckConfig#T]
  )
  private[PlanCheckMacro] final abstract class LiteralString { type T <: String }
  object LiteralString {
    @inline implicit final def apply(s: String): LiteralString { type T = s.type } = null
  }
  private[PlanCheckMacro] sealed abstract class LiteralBoolean { type T <: Boolean }
  object LiteralBoolean {
    @inline implicit final def apply(b: Boolean): LiteralBoolean = macro LiteralBooleanMacro.createBool

    @inline final def True: LiteralBoolean { type T = LiteralCompat.`true`.T } = null
    @inline final def False: LiteralBoolean { type T = LiteralCompat.`false`.T } = null

    object LiteralBooleanMacro {
      def createBool(c: whitebox.Context)(b: c.Expr[Boolean]): c.Tree = {
        import c.universe._
        val bool = b.tree.asInstanceOf[LiteralApi].value.value.asInstanceOf[Boolean]
        val methodName = TermName(bool.toString.capitalize)
        q"${reify(LiteralBoolean)}.$methodName"
      }
    }
  }

  // 2.13+
//  class Impl[T <: PlanHolder, Roles <: String with Singleton, Activations <: String with Singleton](
//    roleAppMain: T,
//    roles: Roles,
//    activations: Activations,
//  )(implicit
//    val planCheck: PlanCheck[T, Roles, Activations]
//  )

  private[this] def instantiateObject[T](u: Universe)(implicit tpe: u.WeakTypeTag[T]): T = {
    val className = s"${tpe.tpe.erasure.typeSymbol.fullName}$$"
    val clazz = Class.forName(className)
    TypeUtil.instantiateObject[T](clazz)
  }

}
