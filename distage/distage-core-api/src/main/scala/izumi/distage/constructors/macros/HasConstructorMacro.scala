package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, HasConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object HasConstructorMacro {

  def mkHasConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[HasConstructor[T]] = {
    val macroUniverse = StaticDIUniverse(c)
    val impls = HasConstructorMacros(c)(macroUniverse)
    import c.universe._
    import impls.{c => _, u => _, _}

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("HasConstructor", targetType)
    val deepIntersection = ReflectionUtil
      .deepIntersectionTypeMembers[c.universe.type](targetType)
      .filter(_ ne definitions.AnyTpe)

    targetType match {
      case definitions.AnyTpe =>
        c.Expr[HasConstructor[T]](q"_root_.izumi.distage.constructors.HasConstructor.empty")

      case _ =>
        ziohasConstructorAssertion(targetType, deepIntersection)

        val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
        val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

        val params = reflectionProvider.zioHasParameters(c.freshName)(deepIntersection)
        val provider: c.Expr[ProviderMagnet[T]] = {
          generateProvider[T, ProviderType.ZIOHas.type](params :: Nil) {
            case (headParam :: params) :: Nil =>
              params.foldLeft(q"_root_.zio.Has.apply($headParam)") {
                (expr, arg) => q"$expr.add($arg)"
              }
            case _ => c.abort(c.enclosingPosition, s"Impossible happened, empty Has intersection or malformed type $targetType in HasConstructorMacro")
          }
        }

        val res = c.Expr[HasConstructor[T]](q"{ new ${weakTypeOf[HasConstructor[T]]}($provider) }")
        logger.log(s"Final syntax tree of HasConstructor for $targetType:\n$res")

        res
    }
  }

}
