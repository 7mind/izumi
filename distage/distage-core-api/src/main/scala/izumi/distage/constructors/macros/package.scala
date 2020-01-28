package izumi.distage.constructors

import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox

package object macros {

  private[macros] def requireConcreteTypeConstructor(c: blackbox.Context)(macroName: String, tpe: c.Type): Unit = {
    if (!ReflectionUtil.allPartsStrong(tpe.dealias.typeConstructor)) {
      c.abort(c.enclosingPosition,
        s"""$macroName: Can't generate constructor for $tpe:
           |Type constructor is an unresolved type parameter `${tpe.dealias.typeConstructor}`.
           |Did you forget to put a $macroName context bound on the ${tpe.dealias.typeConstructor}, such as [${tpe.dealias.typeConstructor}: $macroName]?
           |""".stripMargin)
    }
  }

  private[macros] def mkArgFromAssociation(c: blackbox.Context)
                                          (u: StaticDIUniverse.Aux[c.universe.type])
                                          (logger: TrivialLogger)
                                          (association: u.Association
                                          // parameter: u.Association.Parameter, ctorArgument: u.u.Tree, traitMethodImpl: u.u.Tree, ctorArgumentName: u.u.TermName
                                          ): (u.Association.Parameter, u.u.Tree, (u.u.Tree, u.u.TermName)) = {
    import c.universe._
    import u.Association._

    association match {
      case method: AbstractMethod =>
        val paramTpe = method.symbol.finalResultType
        val methodName = TermName(method.name)
        val freshArgName = c.freshName(methodName)
        // force by-name
        val byNameParamTpe = appliedType(definitions.ByNameParamClass, paramTpe)

        val parameter = method.asParameter
        logger.log(s"original method return: $paramTpe, after by-name: $byNameParamTpe, $parameter")

        (parameter, q"val $freshArgName: $byNameParamTpe", q"final lazy val $methodName: $paramTpe = $freshArgName" -> freshArgName)
      case parameter: Parameter =>
        val paramTpe = parameter.symbol.finalResultType
        val freshArgName = c.freshName(TermName(parameter.name))

        (parameter, q"val $freshArgName: $paramTpe", (null: u.u.Tree) -> freshArgName)
    }
  }

  private[macros] def mkArgFromAssociation0(c: blackbox.Context)
                                          (u: StaticDIUniverse.Aux[c.universe.type])
                                          (logger: TrivialLogger)
                                          (association: u.Association,
                                           idx: Int
                                          // parameter: u.Association.Parameter, ctorArgument: u.u.Tree, traitMethodImpl: u.u.Tree, ctorArgumentName: u.u.TermName
                                          ): (u.Association.Parameter, u.u.Tree, (u.u.Tree, u.u.Tree)) = {
    import c.universe._
    import u.Association._

    association match {
      case method: AbstractMethod =>
        val paramTpe = method.symbol.finalResultType
        val methodName = TermName(method.name)
        val freshArgName = c.freshName(methodName)
        // force by-name
        val byNameParamTpe = appliedType(definitions.ByNameParamClass, paramTpe)

        val parameter = method.asParameter
        logger.log(s"original method return: $paramTpe, after by-name: $byNameParamTpe, $parameter")

        (parameter, q"val $freshArgName: $byNameParamTpe", q"final lazy val $methodName: $paramTpe = $freshArgName" -> q"")
      case parameter: Parameter =>
        val paramTpe = parameter.symbol.finalResultType
        val freshArgName = c.freshName(TermName(parameter.name))

        (parameter, q"val $freshArgName: $paramTpe", (null: u.u.Tree) -> Liftable.liftName(freshArgName))
    }
  }

}
