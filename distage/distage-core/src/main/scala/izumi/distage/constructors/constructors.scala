package izumi.distage.constructors

import izumi.distage.constructors.`macro`.{AnyConstructorMacro, ConcreteConstructorMacro, FactoryConstructorMacro, TraitConstructorMacro}
import izumi.distage.model.providers.ProviderMagnet

import scala.language.experimental.macros

sealed trait AnyConstructor[T] {
  def provider: ProviderMagnet[T]
}
final case class ConcreteConstructor[T](provider: ProviderMagnet[T]) extends AnyConstructor[T]
final case class TraitConstructor[T](provider: ProviderMagnet[T]) extends AnyConstructor[T]
final case class FactoryConstructor[T](provider: ProviderMagnet[T]) extends AnyConstructor[T]

object AnyConstructor {
  def apply[T: AnyConstructor]: AnyConstructor[T] = implicitly

  def generateUnsafeWeakSafeTypes[T]: AnyConstructor[T] = macro AnyConstructorMacro.mkAnyConstructorUnsafeWeakSafeTypes[T]

  implicit def materialize[T]: AnyConstructor[T] = macro AnyConstructorMacro.mkAnyConstructor[T]
}

object ConcreteConstructor {
  def apply[T: ConcreteConstructor]: ConcreteConstructor[T] = implicitly

  implicit def materialize[T]: ConcreteConstructor[T] = macro ConcreteConstructorMacro.mkConcreteConstructor[T]
}

object TraitConstructor {
  def apply[T: TraitConstructor]: TraitConstructor[T] = implicitly

  implicit def materialize[T]: TraitConstructor[T] = macro TraitConstructorMacro.mkTraitConstructor[T]
}

object FactoryConstructor {
  def apply[T: FactoryConstructor]: FactoryConstructor[T] = implicitly

  implicit def materialize[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}
