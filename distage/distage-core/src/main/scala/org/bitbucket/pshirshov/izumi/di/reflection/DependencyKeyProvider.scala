package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull, TypeSymb}
import org.bitbucket.pshirshov.izumi.di.model.{Callable, DIKey}
import org.bitbucket.pshirshov.izumi.di.reflection.DependencyContext.{MethodContext, ParameterContext}

sealed trait DependencyContext {

}


object DependencyContext {

  case class MethodContext(definingClass: TypeFull) extends DependencyContext

  sealed trait ParameterContext extends DependencyContext

  case class ConstructorParameterContext(definingClass: TypeFull, constructor: SelectedConstructor) extends ParameterContext

  case class MethodParameterContext(factoryClass: TypeFull, factoryMethod: MethodSymb) extends ParameterContext

  case class CallableParameterContext(definingCallable: Callable) extends ParameterContext

}

trait DependencyKeyProvider {
  def keyFromParameter(context: ParameterContext, parameterSymbol: TypeSymb): DIKey

  def keyFromMethod(context: MethodContext, methodSymbol: MethodSymb): DIKey

  def keyFromType(parameterSymbol: TypeFull): DIKey
}
