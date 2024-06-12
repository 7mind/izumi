package izumi.distage.constructors

import izumi.distage.reflection.macros.constructors.*
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.exceptions.macros.{TraitInitializationFailedException, UnsupportedDefinitionException}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.SafeType
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.reflect.WeakTag
import zio.ZEnvironment

import scala.language.experimental.macros as enableMacros

sealed trait AnyConstructorBase[T] extends Any with ClassConstructorOptionalMakeDSL[T] {
  def provider: Functoid[T]
}

/**
  * An implicitly summonable constructor for a concrete class `T`
  *
  * @example
  * {{{
  *  import distage.{ClassConstructor, Functoid, Injector, ModuleDef}
  *
  *  class A(val i: Int)
  *
  *  val constructor: Functoid[A] = ClassConstructor[A]
  *
  *  val lifecycle = Injector().produceGet[A](new ModuleDef {
  *    make[A].from(constructor)
  *    make[Int].fromValue(5)
  *  })
  *
  *  lifecycle.use {
  *    (a: A) =>
  *      println(a.i)
  *  }
  * }}}
  *
  * @return [[izumi.distage.model.providers.Functoid]][T] value
  */
final class ClassConstructor[T](val provider: Functoid[T]) extends AnyVal with AnyConstructorBase[T]

object ClassConstructor {
  def apply[T](implicit ctor: ClassConstructor[T]): Functoid[T] = ctor.provider

  implicit def materialize[T]: ClassConstructor[T] = macro ClassConstructorMacro.mkClassConstructor[T]
}

/**
  * An implicitly summonable constructor for a traits or abstract class `T`
  *
  * @see [[https://izumi.7mind.io/distage/basics.html#auto-traits Auto-Traits feature]]
  * @see [[izumi.distage.model.definition.impl]] recommended documenting annotation for use with [[TraitConstructor]]
  *
  * @return [[izumi.distage.model.providers.Functoid]][T] value
  */
final class TraitConstructor[T](val provider: Functoid[T]) extends AnyVal with AnyConstructorBase[T]

object TraitConstructor {
  def apply[T](implicit ctor: TraitConstructor[T]): Functoid[T] = ctor.provider

  implicit def materialize[T]: TraitConstructor[T] = macro TraitConstructorMacro.mkTraitConstructor[T]

  def wrapInitialization[A](init: => A)(implicit weakTag: WeakTag[A]): A = {
    try init
    catch {
      case e: Throwable =>
        val tpe = SafeType.unsafeGetWeak[A]
        throw new TraitInitializationFailedException(s"Failed to initialize trait $tpe. It may be an issue with the trait or a framework bug", tpe, e)
    }
  }
}

/**
  * An implicitly summonable constructor for a "factory-like" trait or abstract class `T`
  *
  * @see [[https://izumi.7mind.io/distage/basics.html#auto-factories Auto-Factories feature]]
  * @see [[izumi.distage.model.definition.impl]] recommended documenting annotation for use with [[FactoryConstructor]]
  *
  * @return [[izumi.distage.model.providers.Functoid]][T] value
  */
final class FactoryConstructor[T](val provider: Functoid[T]) extends AnyVal with AnyConstructorBase[T]

object FactoryConstructor {
  def apply[T](implicit ctor: FactoryConstructor[T]): Functoid[T] = ctor.provider

  implicit def materialize[T]: FactoryConstructor[T] = macro FactoryConstructorMacro.mkFactoryConstructor[T]
}

/**
  * An implicitly summonable constructor for a `ZEnvironment[A & B & C]`
  *
  * `zio.ZEnvironment` heterogeneous map values may be used by ZIO or other Reader-like effects
  *
  * @see [[https://izumi.7mind.io/distage/basics.html#zio-environment-bindings ZIO Environment bindings]]
  */
final class ZEnvConstructor[T](val provider: Functoid[ZEnvironment[T]]) extends AnyVal with AnyConstructorBase[ZEnvironment[T]]

object ZEnvConstructor {
  def apply[T](implicit ctor: ZEnvConstructor[T]): Functoid[ZEnvironment[T]] = ctor.provider

  def empty: ZEnvConstructor[Any] = new ZEnvConstructor(Functoid.pure(ZEnvironment.empty))

  implicit def materialize[T]: ZEnvConstructor[T] = macro ZEnvConstructorMacro.mkZEnvConstructor[T]
}

private[distage] sealed trait ClassConstructorOptionalMakeDSL[T] extends Any {
  def provider: Functoid[T]
}

object ClassConstructorOptionalMakeDSL {
  private[distage] final class Impl[T](val provider: Functoid[T]) extends AnyVal with ClassConstructorOptionalMakeDSL[T]

  @inline def apply[T](functoid: Functoid[T]): ClassConstructorOptionalMakeDSL.Impl[T] = {
    new ClassConstructorOptionalMakeDSL.Impl[T](functoid)
  }

  def errorConstructor[T](tpe: String, nonWhitelistedMethods: List[String]): ClassConstructorOptionalMakeDSL.Impl[T] = {
    ClassConstructorOptionalMakeDSL[T](Functoid.lift(throwError(tpe, nonWhitelistedMethods)))
  }

  def throwError(tpe: String, nonWhitelistedMethods: List[String]): Nothing = {

    throw new UnsupportedDefinitionException(
      s"""`make[$tpe]` DSL failure: Called an empty error constructor, because constructor for $tpe WAS NOT generated.
         |Because after `make` there were following method calls in the same expression:${nonWhitelistedMethods.niceList()}
         |
         |These calls were assumed to be `.from`-like method calls, since they are not in the allowed list: ${ModuleDefDSL.MakeDSLNoOpMethodsWhitelist}
         |The assumption is that all not explicitly allowed calls will eventually call any of `.from`/`.using`/`.todo` and fill in the constructor.
         |""".stripMargin
    )
  }

  implicit def materialize[T]: ClassConstructorOptionalMakeDSL.Impl[T] = macro MakeMacro.classConstructorOptionalMakeDSL[T]
}
