package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.model.Callable
import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull}

sealed trait DependencyContext {

}

object DependencyContext {

  case class MethodContext(definingClass: TypeFull) extends DependencyContext

  sealed trait ParameterContext extends DependencyContext

  case class ConstructorParameterContext(definingClass: TypeFull, constructor: SelectedConstructor) extends ParameterContext

  case class MethodParameterContext(factoryClass: TypeFull, factoryMethod: MethodSymb) extends ParameterContext

  case class CallableParameterContext(definingCallable: Callable) extends ParameterContext

}