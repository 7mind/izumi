package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.provisioning.strategies.{AbstractConstructorMacro, AnyConstructorMacro, FactoryConstructorMacro, TraitConstructorMacro}

import scala.language.experimental.macros

sealed trait AnyConstructor[T]
final case class ConcreteConstructor[T](implType: SafeType) extends AnyConstructor[T]

sealed trait AbstractConstructor[T] extends AnyConstructor[T] {
  def function: DIKeyWrappedFunction[T]
}
final case class TraitConstructor[T](function: DIKeyWrappedFunction[T]) extends AbstractConstructor[T]
final case class FactoryConstructor[T](function: DIKeyWrappedFunction[T]) extends AbstractConstructor[T]

object AnyConstructor {
  implicit def apply[T]: AnyConstructor[T] = macro AnyConstructorMacro.mkAnyConstructor[T]
}

object AbstractConstructor {
  implicit def apply[T]: AbstractConstructor[T] = macro AbstractConstructorMacro.mkAbstractConstructor[T]
}

object TraitConstructor {
  implicit def apply[T]: TraitConstructor[T] = macro TraitConstructorMacro.mkTraitConstructor[T]
}

object FactoryConstructor {
  implicit def apply[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}
