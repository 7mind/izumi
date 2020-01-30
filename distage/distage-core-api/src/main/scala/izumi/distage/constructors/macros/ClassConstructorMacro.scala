package izumi.distage.constructors.macros

import izumi.distage.constructors.{ClassConstructor, DebugProperties}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object ClassConstructorMacro {

  def mkClassConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ClassConstructor[T]] = {
    import c.universe._

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("ClassConstructor", targetType)

    targetType match {
      case t: SingletonTypeApi =>
        val providerMagnet = symbolOf[ProviderMagnet.type].asClass.module
        val term = t match {
          case t: ThisTypeApi => This(t.sym)
          case t: ConstantTypeApi => q"${t.value}"
          case _ => q"${t.termSymbol}"
        }
        c.Expr[ClassConstructor[T]] {
          q"{ new ${weakTypeOf[ClassConstructor[T]]}($providerMagnet.singleton[$targetType]($term)) }"
        }

      case _ =>
        val macroUniverse = StaticDIUniverse(c)


        val uttils = ConstructorMacros(c)(macroUniverse)
        import uttils.{c => _, u => _, _}


        val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
        if (!reflectionProvider.isConcrete(targetType)) {
          c.abort(c.enclosingPosition,
            s"""Tried to derive constructor function for class $targetType, but the class is an
               |abstract class or a trait! Only concrete classes (`class` keyword) are supported""".stripMargin)
        }

        val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

        val providerMagnet: c.Expr[ProviderMagnet[T]] = mkClassConstructorProvider(reflectionProvider)(targetType)

        val res = c.Expr[ClassConstructor[T]] {
          q"{ new ${weakTypeOf[ClassConstructor[T]]}($providerMagnet) }"
        }
        logger.log(s"Final syntax tree of class for $targetType:\n$res")
        res
    }
  }

}

