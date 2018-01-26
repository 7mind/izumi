package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.Callable
import com.github.pshirshov.izumi.distage.{MethodSymb, TypeFull}

sealed trait DependencyContext {

}

object DependencyContext {

  case class MethodContext(definingClass: TypeFull) extends DependencyContext

  sealed trait ParameterContext extends DependencyContext

  case class ConstructorParameterContext(definingClass: TypeFull, constructor: SelectedConstructor) extends ParameterContext

  case class MethodParameterContext(factoryClass: TypeFull, factoryMethod: MethodSymb) extends ParameterContext

  case class CallableParameterContext(definingCallable: Callable) extends ParameterContext

}