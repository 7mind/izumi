package izumi.distage.constructors

import izumi.distage.constructors.macros.{AnyConstructorMacro, ConcreteConstructorMacro, FactoryConstructorMacro, TraitConstructorMacro}
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.exceptions.{TraitInitializationFailedException, UnsupportedDefinitionException}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType

import scala.language.experimental.{macros => enableMacros}

sealed trait AnyConstructor[T] extends AnyConstructorOptionalMakeDSL[T] {
  def provider: ProviderMagnet[T]

  // FIXME: better immutable provider equality scheme ???
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
      case e: Throwable =>
        throw new TraitInitializationFailedException(s"Failed to initialize trait $tpe. It may be an issue with the trait or a framework bug", tpe, e)
    }
  }
}

object FactoryConstructor {
  def apply[T: FactoryConstructor]: FactoryConstructor[T] = implicitly

  implicit def materialize[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}

sealed trait AnyConstructorOptionalMakeDSL[T] {
  def provider: ProviderMagnet[T]
}
object AnyConstructorOptionalMakeDSL {
  def error[T](tpe: String, nonWhitelistedMethods: Set[String]): AnyConstructorOptionalMakeDSL[T] = {
    import izumi.fundamentals.platform.strings.IzString._
    AnyConstructorOptionalMakeDSL[T](ConcreteConstructor[T](ProviderMagnet.lift[Nothing] {
      throw new UnsupportedDefinitionException(
        s"`make[$tpe]` DSL failure: Called an empty error constructor, constructor for $tpe WAS NOT generated because after" +
        s""" `make` call there were following method calls in the same expression:${nonWhitelistedMethods.niceList()}
             |
             |The assumption is that all method calls that aren't in ${ModuleDefDSL.MakeDSLNoOpMethodsWhitelist}
             |Will eventually call `.from`/`.using`/`.todo` and fill in the constructor.
             |""".stripMargin
      )
    }))
  }

  def apply[T](anyConstructor: AnyConstructor[T]): AnyConstructorOptionalMakeDSL[T] = {
    new AnyConstructorOptionalMakeDSL[T] {
      override val provider: ProviderMagnet[T] = anyConstructor.provider
    }
  }

  implicit def materialize[T]: AnyConstructorOptionalMakeDSL[T] = macro AnyConstructorMacro.optional[T]
}
