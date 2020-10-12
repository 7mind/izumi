package izumi.distage.framework

import izumi.distage.framework.PlanCheck.checkRoleApp
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.StaticPluginLoader.StaticPluginLoaderMacro
import izumi.distage.roles.PlanHolder
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.reflection.TypeUtil

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

object PlanCheckMacro {

  final class PerformPlanCheck[T <: PlanHolder, Roles <: String, Activations <: String, Config <: String](
    roleAppMain: T,
    roles: Roles,
    activations: Activations,
    config: Config,
  ) {
    def run(): Unit = checkRoleApp(roleAppMain, roles, activations, config)
  }
  object PerformPlanCheck {
    implicit def performCompileTimeCheck[T <: PlanHolder, Roles <: String, Activations <: String, Config <: String]: PerformPlanCheck[T, Roles, Activations, Config] =
      macro Materialize.impl[T, Roles, Activations, Config]
  }

  object Materialize {
    def impl[RoleAppMain <: PlanHolder: c.WeakTypeTag, Roles <: String: c.WeakTypeTag, Activations <: String: c.WeakTypeTag, Config <: String: c.WeakTypeTag](
      c: blackbox.Context
    ): c.Expr[PerformPlanCheck[RoleAppMain, Roles, Activations, Config]] = {
      import c.universe._

      def getConstantType[S: c.WeakTypeTag]: String = {
        weakTypeOf[S].dealias match {
          case ConstantType(Constant(s: String)) => s
          case tpe =>
            c.abort(
              c.enclosingPosition,
              s"""When materializing ${weakTypeOf[PerformPlanCheck[RoleAppMain, Roles, Activations, Config]]},
                 |Bad String constant type: $tpe - Not a constant! Only constant string literal types are supported!
               """.stripMargin,
            )
        }
      }

      val t = c.Expr[RoleAppMain](q"${weakTypeOf[RoleAppMain].asInstanceOf[SingleTypeApi].sym.asTerm}")
//      val roles = c.Expr[Roles](Liftable.liftString(getConstantType[Roles]))
//      val activations = c.Expr[Activations](Liftable.liftString(getConstantType[Activations]))
//      val config = c.Expr[Config](Liftable.liftString(getConstantType[Config]))
//      reify {
//        new PerformPlanCheck[T, Roles, Activations, Config](t.splice, roles.splice, activations.splice, config.splice)
//      }

      val roles = getConstantType[Roles]
      val activations = getConstantType[Activations]
      val config = getConstantType[Config]

      // err, do in place?

      // FIXME: splice plugin references

      val maybeMain = instantiateObject[RoleAppMain](c.universe)
      val pluginsLoadedByRoleApp =
        try {
          PlanCheck.checkRoleApp(maybeMain, roles, activations, config)
        } catch {
          case t: Throwable =>
            c.abort(c.enclosingPosition, t.stackTrace)
        }

      // filter out anonymous classes that can't be referred in code
      // & those that aren't suitable for splicing a `new` call / being loaded by PluginLoader
      val referencablePlugins = pluginsLoadedByRoleApp
        .allRaw
        .filterNot(_.getClass.isAnonymousClass)
        .filter(p => TypeUtil.isObject(p.getClass).isDefined || TypeUtil.isZeroArgClass(p.getClass).isDefined)
      // we HAVE
      val pluginsList: List[c.Tree] = StaticPluginLoaderMacro.instantiatePluginsInCode(c)(referencablePlugins)

//      def referencePluginsInCode(c: blackbox.Context)(loadedPlugins: Seq[PluginBase]): List[c.Tree] = {
//        import c.universe._
//        loadedPlugins.map {
//          plugin =>
//            val clazz = plugin.getClass
//            val runtimeMirror = scala.reflect.runtime.universe.runtimeMirror(clazz.getClassLoader)
//            val runtimeClassSymbol = runtimeMirror.classSymbol(clazz)
//
//            val macroMirror: Mirror = c.mirror
//
//            if (runtimeClassSymbol.isModuleClass) {
//              val tgt = macroMirror.staticModule(runtimeClassSymbol.module.fullName)
//              q"_root_.scala.Predef.identity(null : $tgt)"
//            } else {
//              val tgt = macroMirror.staticClass(runtimeClassSymbol.fullName)
//              q"_root_.scala.Predef.identity(null : $tgt)"
//            }
//        }.toList
//      }
//      val pluginsList: List[c.Tree] = referencePluginsInCode(c)(referencablePlugins)

      val referenceStmt = c.Expr[Unit](q"{ lazy val _ = $pluginsList ; () }")
      def str[T <: String: c.WeakTypeTag](s: String): c.Expr[T] = c.Expr[T](Literal(Constant(s)))

      reify {
        referenceStmt.splice
        new PerformPlanCheck[RoleAppMain, Roles, Activations, Config](
          t.splice,
          str[Roles](roles).splice,
          str[Activations](activations).splice,
          str[Config](config).splice,
        )
      }
    }
  }

  // 2.12 requires `Witness`
  class Impl[RoleAppMain <: PlanHolder, Roles <: LiteralString, Activations <: LiteralString, Config <: LiteralString](
    roleAppMain: RoleAppMain,
    roles: Roles with LiteralString = LiteralString("*"),
    activations: Activations with LiteralString = LiteralString("*"),
    config: Config with LiteralString = LiteralString("*"),
  )(implicit
    val planCheck: PerformPlanCheck[RoleAppMain, Roles#T, Activations#T, Config#T]
  )
  private[PlanCheckMacro] final abstract class LiteralString { type T <: String }
  object LiteralString {
    @inline implicit final def apply(s: String): LiteralString { type T = s.type } = null
  }
  private[PlanCheckMacro] final abstract class LiteralBoolean { type T <: Boolean }
  object LiteralBoolean {
    @inline implicit final def apply(b: Boolean): LiteralBoolean { type T = b.type } = null
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
