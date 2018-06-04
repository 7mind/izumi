package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.provisioning.strategies.{AnyConstructorMacro, ConcreteConstructorMacro, FactoryConstructorMacro, TraitConstructorMacro}

import scala.language.experimental.macros

sealed trait AnyConstructor[T] {
  def provider: ProviderMagnet[T]
}

final case class ConcreteConstructor[T](provider: ProviderMagnet[T]) extends AnyConstructor[T]

final case class TraitConstructor[T](provider: ProviderMagnet[T]) extends AnyConstructor[T]

final case class FactoryConstructor[T](provider: ProviderMagnet[T]) extends AnyConstructor[T]

object AnyConstructor {
  def apply[T: AnyConstructor]: AnyConstructor[T] = implicitly[AnyConstructor[T]]

  implicit def derive[T]: AnyConstructor[T] = macro AnyConstructorMacro.mkAnyConstructor[T]
}

object ConcreteConstructor {
  def apply[T: ConcreteConstructor]: ConcreteConstructor[T] = implicitly[ConcreteConstructor[T]]

  implicit def derive[T]: ConcreteConstructor[T] = macro ConcreteConstructorMacro.mkConcreteConstructor[T]
}

object TraitConstructor {
  def apply[T: TraitConstructor]: TraitConstructor[T] = implicitly[TraitConstructor[T]]

  implicit def derive[T]: TraitConstructor[T] = macro TraitConstructorMacro.mkTraitConstructor[T]
}

object FactoryConstructor {
  def apply[T: FactoryConstructor]: FactoryConstructor[T] = implicitly[FactoryConstructor[T]]

  implicit def derive[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}
