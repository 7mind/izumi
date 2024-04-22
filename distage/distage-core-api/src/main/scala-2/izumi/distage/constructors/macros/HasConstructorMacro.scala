package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, ZEnvConstructor}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.ReflectionProviderDefaultImpl
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}
import zio.ZEnvironment

import scala.reflect.macros.blackbox

object ZEnvConstructorMacro {

  def mkZEnvConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ZEnvConstructor[T]] = {
    val macroUniverse = StaticDIUniverse(c)
    val impls = ZEnvConstructorMacros(c)(macroUniverse)
    import c.universe._
    import impls.{c => _, u => _, _}

    val targetType = weakTypeOf[T].dealias
    requireConcreteTypeConstructor(c)("ZEnvConstructor", targetType)

    targetType match {
      case definitions.AnyTpe =>
        c.Expr[ZEnvConstructor[T]](q"_root_.izumi.distage.constructors.ZEnvConstructor.empty")

      case _ =>
        val deepIntersection = ReflectionUtil
          .deepIntersectionTypeMembersNoNorm[c.universe.type](targetType)
          .filter(_ ne definitions.AnyTpe)

        val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
        val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`.name)

        val params = reflectionProvider.zioHasParameters(c.freshName)(deepIntersection)
        val provider: c.Expr[Functoid[ZEnvironment[T]]] = {
          generateProvider[ZEnvironment[T], ProviderType.ZIOEnvironment.type](params :: Nil) {
            case (headParam :: params) :: Nil =>
              params.foldLeft(q"_root_.zio.ZEnvironment.apply($headParam)") {
                (expr, arg) => q"$expr.add($arg)"
              }
            case _ => c.abort(c.enclosingPosition, s"Impossible happened, empty Has intersection or malformed type $targetType in ZEnvConstructorMacro")
          }
        }

        val res = c.Expr[ZEnvConstructor[T]](q"{ new ${weakTypeOf[ZEnvConstructor[T]]}($provider) }")
        logger.log(s"Final syntax tree of ZEnvConstructor for $targetType:\n$res")

        res
    }
  }

}
