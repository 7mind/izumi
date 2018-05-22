package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.provisioning.strategies.{AbstractConstructorMacro, AnyConstructorMacro, FactoryConstructorMacro, TraitConstructorMacro}

import scala.language.experimental.macros

sealed trait AnyConstructor[T]

sealed trait AbstractConstructor[T] extends AnyConstructor[T] {
  def function: DIKeyWrappedFunction[T]
}

final case class ConcreteConstructor[T](implType: SafeType) extends AnyConstructor[T]

final case class TraitConstructor[T](function: DIKeyWrappedFunction[T], tree: Seq[u.Annotation]) extends AbstractConstructor[T]

final case class FactoryConstructor[T](function: DIKeyWrappedFunction[T]) extends AbstractConstructor[T]

object AnyConstructor {
  def apply[T: AnyConstructor]: AnyConstructor[T] = implicitly[AnyConstructor[T]]

  implicit def derive[T]: AnyConstructor[T] = macro AnyConstructorMacro.mkAnyConstructor[T]
}

object AbstractConstructor {
  def apply[T: AbstractConstructor]: AbstractConstructor[T] = implicitly[AbstractConstructor[T]]

  implicit def derive[T]: AbstractConstructor[T] = macro AbstractConstructorMacro.mkAbstractConstructor[T]
}

object TraitConstructor {
  def apply[T: TraitConstructor]: TraitConstructor[T] = implicitly[TraitConstructor[T]]

  implicit def derive[T]: TraitConstructor[T] = macro TraitConstructorMacro.mkTraitConstructor[T]
}

object FactoryConstructor {
  def apply[T: FactoryConstructor]: FactoryConstructor[T] = implicitly[FactoryConstructor[T]]

  implicit def derive[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}
