package izumi.distage.constructors

import izumi.distage.constructors.macros.{AnyConstructorMacro, ConcreteConstructorMacro, FactoryConstructorMacro, TraitConstructorMacro}
import izumi.distage.model.exceptions.TraitInitializationFailedException
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType

import scala.language.experimental.{macros => enableMacros}

sealed trait AnyConstructor[T] {
  def provider: ProviderMagnet[T]

  // FIXME: better provider equality scheme ???
  provider.get.asGenerated
  // FIXME: better immutable provider equality scheme ???
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

  def wrapInitialization[A](tpe: SafeType)(init: => A): A = {
    try init catch {
      case e: AbstractMethodError =>
        throw new TraitInitializationFailedException(s"TODO: Failed to initialize trait $tpe. Probably it contains fields (val or var) though fields are not supported yet, see https://github.com/7mind/izumi/issues/26", tpe, e)
      case e: Throwable =>
        throw new TraitInitializationFailedException(s"Failed to initialize trait $tpe. It may be an issue with the trait or a framework bug", tpe, e)
    }
  }
}

object FactoryConstructor {
  def apply[T: FactoryConstructor]: FactoryConstructor[T] = implicitly

  implicit def materialize[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}
