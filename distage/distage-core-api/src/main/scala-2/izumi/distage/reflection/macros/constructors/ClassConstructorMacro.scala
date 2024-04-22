package izumi.distage.reflection.macros.constructors

import izumi.distage.constructors.{ClassConstructor, DebugProperties}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.universe.{ReflectionProviderDefaultImpl, StaticDIUniverse}
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.annotation.nowarn
import scala.reflect.macros.blackbox

object ClassConstructorMacro {

  def mkClassConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ClassConstructor[T]] = {
    import c.universe.*

    val macroUniverse = StaticDIUniverse(c)
    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("ClassConstructor", targetType)

    if (reflectionProvider.isConcrete(targetType)) {
      (targetType match {
        case t: SingletonTypeApi =>
          val functoid = symbolOf[Functoid.type].asClass.module
          val term = t match {
            case t: ThisTypeApi => This(t.sym)
            case t: ConstantTypeApi => q"${t.value}"
            case _ => q"${t.termSymbol}"
          }
          c.Expr[ClassConstructor[T]] {
            q"{ new ${weakTypeOf[ClassConstructor[T]]}($functoid.singleton[$targetType]($term)) }"
          }

        case _ =>
          val impls = ClassConstructorMacros(c)(macroUniverse)

          val provider: c.Expr[Functoid[T]] = impls.mkClassConstructorProvider(reflectionProvider)(targetType)

          val res = c.Expr[ClassConstructor[T]](q"{ new ${weakTypeOf[ClassConstructor[T]]}($provider) }")

          val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`.name)
          logger.log(s"Final syntax tree of class for $targetType:\n$res")

          res
      }): @nowarn("msg=outer reference")
    } else if (reflectionProvider.isWireableAbstract(targetType)) {
      c.abort(
        c.enclosingPosition,
        s"ClassConstructor failure: $targetType is a trait or an abstract class, use `makeTrait` or `make[X].fromTrait` to wire traits.",
      )
    } else if (reflectionProvider.isFactory(targetType)) {
      c.abort(
        c.enclosingPosition,
        s"ClassConstructor failure: $targetType is a Factory, use `makeFactory` or `make[X].fromFactory` to wire factories.",
      )
    } else {
      c.abort(
        c.enclosingPosition,
        s"""ClassConstructor failure: couldn't derive a constructor for $targetType!
           |It's neither a concrete class, nor a wireable trait or abstract class!""".stripMargin,
      )
    }

  }

}
