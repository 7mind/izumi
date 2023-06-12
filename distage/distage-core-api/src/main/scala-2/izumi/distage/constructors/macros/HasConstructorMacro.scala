package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, HasConstructor}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}
import zio.ZEnvironment

import scala.reflect.macros.blackbox

object HasConstructorMacro {

  def mkHasConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[HasConstructor[T]] = {
    val macroUniverse = StaticDIUniverse(c)
    val impls = HasConstructorMacros(c)(macroUniverse)
    import c.universe._
    import impls.{c => _, u => _, _}

    val targetType = weakTypeOf[T].dealias
    requireConcreteTypeConstructor(c)("HasConstructor", targetType)

    targetType match {
      case definitions.AnyTpe =>
        c.Expr[HasConstructor[T]](q"_root_.izumi.distage.constructors.HasConstructor.empty")

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
            case _ => c.abort(c.enclosingPosition, s"Impossible happened, empty Has intersection or malformed type $targetType in HasConstructorMacro")
          }
        }

        val res = c.Expr[HasConstructor[T]](q"{ new ${weakTypeOf[HasConstructor[T]]}($provider) }")
        logger.log(s"Final syntax tree of HasConstructor for $targetType:\n$res")

        res
    }
  }

}
