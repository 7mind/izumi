package izumi.distage.constructors

import izumi.distage.constructors.macros._
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.exceptions.{TraitInitializationFailedException, UnsupportedDefinitionException}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.SafeType
import izumi.fundamentals.reflection.Tags.WeakTag
import zio.Has

import scala.language.experimental.{macros => enableMacros}

sealed trait AnyConstructor[T] extends Any with AnyConstructorOptionalMakeDSL[T]
final class ClassConstructor[T](val provider: ProviderMagnet[T]) extends AnyVal with AnyConstructor[T]
final class TraitConstructor[T](val provider: ProviderMagnet[T]) extends AnyVal with AnyConstructor[T]
final class FactoryConstructor[T](val provider: ProviderMagnet[T]) extends AnyVal with AnyConstructor[T]
final class HasConstructor[T](val provider: ProviderMagnet[T]) extends AnyVal with AnyConstructor[T]

object AnyConstructor {
  def apply[T](implicit ctor: AnyConstructor[T]): ProviderMagnet[T] = ctor.provider

  implicit def materialize[T]: AnyConstructor[T] = macro AnyConstructorMacro.mkAnyConstructor[T]
}

object ClassConstructor {
  def apply[T](implicit ctor: ClassConstructor[T]): ProviderMagnet[T] = ctor.provider

  implicit def materialize[T]: ClassConstructor[T] = macro ClassConstructorMacro.mkClassConstructor[T]
}

object TraitConstructor {
  def apply[T](implicit ctor: TraitConstructor[T]): ProviderMagnet[T] = ctor.provider

  implicit def materialize[T]: TraitConstructor[T] = macro TraitConstructorMacro.mkTraitConstructor[T]

  def wrapInitialization[A](init: => A)(implicit weakTag: WeakTag[A]): A = {
    try init catch {
      case e: Throwable =>
        val tpe = SafeType.unsafeGetWeak[A]
        throw new TraitInitializationFailedException(s"Failed to initialize trait $tpe. It may be an issue with the trait or a framework bug", tpe, e)
    }
  }
}

object FactoryConstructor {
  def apply[T](implicit ctor: FactoryConstructor[T]): ProviderMagnet[T] = ctor.provider

  implicit def materialize[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}

object HasConstructor {
  def apply[T](implicit ctor: HasConstructor[T]): ProviderMagnet[T] = ctor.provider

  implicit def materialize[T <: Has[_]]: HasConstructor[T] = macro HasConstructorMacro.mkHasConstructor[T]
}

private[constructors] sealed trait AnyConstructorOptionalMakeDSL[T] extends Any {
  def provider: ProviderMagnet[T]
}
object AnyConstructorOptionalMakeDSL {
  private[constructors] final class Impl[T](val provider: ProviderMagnet[T]) extends AnyVal with AnyConstructorOptionalMakeDSL[T]

  @inline def apply[T](providerMagnet: ProviderMagnet[T]): AnyConstructorOptionalMakeDSL.Impl[T] = {
    new AnyConstructorOptionalMakeDSL.Impl[T](providerMagnet)
  }

  def errorConstructor[T](tpe: String, nonWhitelistedMethods: List[String]): AnyConstructorOptionalMakeDSL.Impl[T] = {
    AnyConstructorOptionalMakeDSL[T](ProviderMagnet.lift(throwError(tpe, nonWhitelistedMethods)))
  }

  def throwError(tpe: String, nonWhitelistedMethods: List[String]): Nothing = {
    import izumi.fundamentals.platform.strings.IzString._

    throw new UnsupportedDefinitionException(
      s"""`make[$tpe]` DSL failure: Called an empty error constructor, because constructor for $tpe WAS NOT generated.
         |Because after `make` there were following method calls in the same expression:${nonWhitelistedMethods.niceList()}
         |
         |These calls were assumed to be `.from`-like method calls, since they are in the white-list: ${ModuleDefDSL.MakeDSLNoOpMethodsWhitelist}
         |The assumption is that all non-whitelisted calls will eventually call any of `.from`/`.using`/`.todo` and fill in the constructor.
         |""".stripMargin
    )
  }

  implicit def materialize[T]: AnyConstructorOptionalMakeDSL.Impl[T] = macro AnyConstructorMacro.anyConstructorOptionalMakeDSL[T]
}
