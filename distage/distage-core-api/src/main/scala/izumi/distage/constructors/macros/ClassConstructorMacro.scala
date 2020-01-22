package izumi.distage.constructors.macros

import izumi.distage.constructors.{ClassConstructor, DebugProperties}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.ReflectionProvider
import izumi.distage.model.reflection.macros.ProviderMagnetMacro0
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.platform.console.TrivialLogger
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
        val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
        val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

        val (associations, constructor) = mkClassConstructorUnwrapped(c)(macroUniverse)(reflectionProvider, logger)(targetType)

        val provided: c.Expr[ProviderMagnet[T]] = {
          val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
          providerMagnetMacro.generateProvider[T](
            parameters = associations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
            fun = constructor,
            isGenerated = true
          )
        }

        val res = c.Expr[ClassConstructor[T]] {
          q"{ new ${weakTypeOf[ClassConstructor[T]]}($provided) }"
        }
        logger.log(s"Final syntax tree of class for $targetType:\n$res")
        res
    }
  }

  private[macros] def mkClassConstructorUnwrapped(c: blackbox.Context)
                                                 (macroUniverse: StaticDIUniverse.Aux[c.universe.type])
                                                 (reflectionProvider: ReflectionProvider.Aux[macroUniverse.type],
                                                      logger: TrivialLogger)
                                                 (targetType: c.Type): (List[macroUniverse.Association.Parameter], c.Tree) = {
    import c.universe._

    if (!reflectionProvider.isConcrete(targetType)) {
      c.abort(c.enclosingPosition,
        s"""Tried to derive constructor function for class $targetType, but the class is an
           |abstract class or a trait! Only concrete classes (`class` keyword) are supported""".stripMargin)
    } else {
      val (associations, ctorArgs, ctorArgNamesLists) = mkClassConstructorUnwrappedImpl(c)(macroUniverse)(reflectionProvider, logger)(targetType)
      (associations, q"(..$ctorArgs) => new $targetType(...$ctorArgNamesLists)")
    }
  }

  private[macros] def mkClassConstructorUnwrappedImpl(c: blackbox.Context)
                                                     (macroUniverse: StaticDIUniverse.Aux[c.universe.type])
                                                     (reflectionProvider: ReflectionProvider.Aux[macroUniverse.type],
                                                      logger: TrivialLogger)
                                                     (targetType: c.Type): (List[macroUniverse.Association.Parameter], List[c.Tree], List[List[c.universe.TermName]]) = {
    val paramLists = reflectionProvider.constructorParameterLists(targetType)
    val fnArgsNamesLists = paramLists.map(_.map(mkArgFromAssociation(c)(macroUniverse)(logger)(_)))

    val (associations, ctorArgs) = fnArgsNamesLists.flatten.map { case (p, a, _) => (p, a) }.unzip
    val ctorArgNamesLists = fnArgsNamesLists.map(_.map(_._3._2))

    (associations, ctorArgs, ctorArgNamesLists)
  }

}

