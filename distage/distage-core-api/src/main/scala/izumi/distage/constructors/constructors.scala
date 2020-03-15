package izumi.distage.constructors

import izumi.distage.constructors.macros._
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.exceptions.{TraitInitializationFailedException, UnsupportedDefinitionException}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.{Provider, SafeType}
import izumi.fundamentals.reflection.Tags.WeakTag

import scala.language.experimental.{macros => enableMacros}

sealed trait AnyConstructor[T] extends AnyConstructorOptionalMakeDSL[T]
final class ClassConstructor[T](get: Provider) extends ProviderMagnet[T](get) with AnyConstructor[T]
final class TraitConstructor[T](get: Provider) extends ProviderMagnet[T](get) with AnyConstructor[T]
final class FactoryConstructor[T](get: Provider) extends ProviderMagnet[T](get) with AnyConstructor[T]

object AnyConstructor {
  def apply[T: AnyConstructor]: AnyConstructor[T] = implicitly

  implicit def materialize[T]: AnyConstructor[T] = macro AnyConstructorMacro.mkAnyConstructor[T]
}

object ClassConstructor {
  def apply[T: ClassConstructor]: ClassConstructor[T] = implicitly

  implicit def materialize[T]: ClassConstructor[T] = macro ClassConstructorMacro.mkClassConstructor[T]
}

object TraitConstructor {
  def apply[T: TraitConstructor]: TraitConstructor[T] = implicitly

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
  def apply[T: FactoryConstructor]: FactoryConstructor[T] = implicitly

  implicit def materialize[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}

private[constructors] sealed trait AnyConstructorOptionalMakeDSL[T] extends ProviderMagnet[T]
object AnyConstructorOptionalMakeDSL {
  def errorConstructor[T](tpe: String, nonWhitelistedMethods: List[String]): AnyConstructorOptionalMakeDSL[T] = {
    new ProviderMagnet[T](ProviderMagnet.lift0(throwError(tpe, nonWhitelistedMethods))) with AnyConstructorOptionalMakeDSL[T]
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

  implicit def materialize[T]: AnyConstructorOptionalMakeDSL[T] = macro AnyConstructorMacro.anyConstructorOptionalMakeDSL[T]
}
