package izumi.distage.model.providers

import izumi.distage.model.exceptions.TODOBindingException
import izumi.distage.model.reflection.macros.FunctoidMacro
import izumi.distage.model.reflection.LinkedParameter
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection._
import izumi.fundamentals.platform.language.{CodePositionMaterializer, unused}
import izumi.fundamentals.platform.language.Quirks._
import izumi.reflect.Tag

import scala.annotation.unchecked.uncheckedVariance
import scala.language.experimental.macros
import scala.language.implicitConversions

/**
  * A function that receives its arguments from DI object graph, including named instances via [[izumi.distage.model.definition.Id]] annotation.
  *
  * The following syntaxes are supported by extractor macro:
  *
  * Inline lambda:
  *
  * {{{
  *   make[Unit].from {
  *     i: Int @Id("special") => ()
  *   }
  * }}}
  *
  * Method reference:
  *
  * {{{
  *   def constructor(@Id("special") i: Int): Unit = ()
  *
  *   make[Unit].from(constructor _)
  *
  *   make[Unit].from(constructor(_))
  * }}}
  *
  * Function value with an annotated signature:
  *
  * {{{
  *   val constructor: (Int @Id("special"), String @Id("special")) => Unit = (_, _) => ()
  *
  *   make[Unit].from(constructor)
  * }}}
  *
  * Using intermediate vals will lose annotations when converting a method into a function value,
  * Prefer passing inline lambdas such as `{ x => y }` or method references such as `(method _)` or `(method(_))`.:
  *
  * {{{
  *   def constructorMethod(@Id("special") i: Int): Unit = ()
  *
  *   val constructor = constructorMethod _
  *
  *   make[Unit].from(constructor) // SURPRISE: Will summon regular Int, not a "special" Int from DI object graph
  *   make[Unit].from(constructorMethod _) // Will work correctly: summon "special" Int
  * }}}
  *
  * Prefer annotating parameter types, not parameters: `class X(i: Int @Id("special")) { ... }`
  *
  * {{{
  *   final case class X(i: Int @Id("special"))
  *
  *   make[X].from(X.apply _) // summons special Int
  * }}}
  *
  * Functoid forms an applicative functor via its  [[izumi.distage.model.providers.Functoid.pure]] & [[izumi.distage.model.providers.Functoid#map2]] methods
  *
  * @note `javax.inject.Named` annotation is also supported
  *
  * @see [[izumi.distage.model.reflection.macros.FunctoidMacro]]]
  * @see Functoid is based on the Magnet Pattern: [[http://spray.io/blog/2012-12-13-the-magnet-pattern/]]
  * @see Essentially Functoid is a function-like entity with additional properties, so it's funny name is reasonable enough: [[https://en.wiktionary.org/wiki/-oid#English]]
  */
final case class Functoid[+A](get: Provider) {
  def map[B: Tag](f: A => B): Functoid[B] = {
    copy[B](get = get.unsafeMap(SafeType.get[B], (any: Any) => f(any.asInstanceOf[A])))
  }

  def zip[B](that: Functoid[B]): Functoid[(A, B)] = {
    implicit val tagA: Tag[A] = this.getRetTag
    implicit val tagB: Tag[B] = that.getRetTag
    tagA.discard() // used for assembling Tag[(A, B)] below
    tagB.discard()
    copy[(A, B)](get = get.unsafeZip(SafeType.get[(A, B)], that.get))
  }

  def map2[B, C: Tag](that: Functoid[B])(f: (A, B) => C): Functoid[C] = {
    zip(that).map[C](f.tupled)
  }

  /** Applicative's `ap` method - can be used to chain transformations like `flatMap`.
    * Apply a function produced by `that` Provider to the value produced by `this` Provider
    */
  def flatAp[B: Tag](that: Functoid[A => B]): Functoid[B] = {
    map2(that) { case (a, f) => f(a) }
  }

  /** Apply a function produced by `this` Provider to the argument produced by `that` Provider */
  def ap[B, C](that: Functoid[B])(implicit @unused ev: A <:< (B => C), tag: Tag[C]): Functoid[C] = {
    that.flatAp[C](this.asInstanceOf[Functoid[B => C]])
  }

  /** Add `B` as an unused dependency of this Provider */
  def addDependency[B: Tag]: Functoid[A] = addDependency(DIKey.get[B])
  def addDependency(key: DIKey): Functoid[A] = addDependencies(key :: Nil)
  def addDependencies(keys: Iterable[DIKey]): Functoid[A] = copy[A](get = get.addUnused(keys))

  @inline private def getRetTag: Tag[A @uncheckedVariance] = Tag(get.ret.cls, get.ret.tag)
}

object Functoid {
  implicit def apply[R](fun: () => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): Functoid[R] = macro FunctoidMacro.impl[R]

  def todoProvider(key: DIKey)(implicit pos: CodePositionMaterializer): Functoid[Nothing] =
    new Functoid[Nothing](
      Provider.ProviderImpl(
        parameters = Seq.empty,
        ret = key.tpe,
        fun = _ => throw new TODOBindingException(s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.get}!", key, pos),
        providerType = ProviderType.Function,
      )
    )

  def identity[A: Tag]: Functoid[A] = identityKey(DIKey.get[A]).asInstanceOf[Functoid[A]]

  def pure[A: Tag](a: A): Functoid[A] = lift(a)

  def unit: Functoid[Unit] = pure(())

  def lift[A: Tag](a: => A): Functoid[A] = {
    new Functoid[A](
      Provider.ProviderImpl[A](
        parameters = Seq.empty,
        ret = SafeType.get[A],
        originalFun = () => a,
        fun = (_: Seq[Any]) => a,
        providerType = ProviderType.Function,
      )
    )
  }

  def singleton[A <: Singleton: Tag](a: A): Functoid[A] = {
    new Functoid[A](
      Provider.ProviderImpl[A](
        parameters = Seq.empty,
        ret = SafeType.get[A],
        fun = (_: Seq[Any]) => a,
        providerType = ProviderType.Singleton,
      )
    )
  }

  def single[A: Tag, B: Tag](f: A => B): Functoid[B] = {
    val key = DIKey.get[A]
    val tpe = key.tpe
    val retTpe = SafeType.get[B]
    val symbolInfo = firstParamSymbolInfo(tpe)

    new Functoid[B](
      Provider.ProviderImpl(
        parameters = Seq(LinkedParameter(symbolInfo, key)),
        ret = retTpe,
        originalFun = f,
        fun = (s: Seq[Any]) => f(s.head.asInstanceOf[A]),
        providerType = ProviderType.Function,
      )
    )
  }

  def identityKey(key: DIKey): Functoid[_] = {
    val tpe = key.tpe
    val symbolInfo = firstParamSymbolInfo(tpe)

    new Functoid(
      Provider.ProviderImpl(
        parameters = Seq(LinkedParameter(symbolInfo, key)),
        ret = tpe,
        fun = (_: Seq[Any]).head,
        providerType = ProviderType.Function,
      )
    )
  }

  implicit final class SyntaxMapSame[A](private val functoid: Functoid[A]) extends AnyVal {
    def mapSame(f: A => A): Functoid[A] = functoid.map(f)(functoid.getRetTag)
  }

  @inline private[this] def firstParamSymbolInfo(tpe: SafeType): SymbolInfo = {
    SymbolInfo(
      name = "x$1",
      finalResultType = tpe,
      isByName = false,
      wasGeneric = false,
    )
  }
}
