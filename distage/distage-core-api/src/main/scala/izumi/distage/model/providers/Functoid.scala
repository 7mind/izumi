package izumi.distage.model.providers

import izumi.distage.constructors.{AnyConstructor, ClassConstructor, FactoryConstructor, HasConstructor, TraitConstructor}
import izumi.distage.model.definition.Identifier
import izumi.distage.model.exceptions.runtime.TODOBindingException
import izumi.distage.model.reflection.LinkedParameter
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.*
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.Quirks.*
import izumi.reflect.Tag

import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.unused

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

  /**
    * Applicative's `ap` method - can be used to chain transformations like `flatMap`.
    *
    * Apply a function produced by `that` Functoid to the value produced by `this` Functoid.
    *
    * Same as
    * {{{
    *   this.map2(that)((a, f) => f(a))
    * }}}
    */
  def flatAp[B: Tag](that: Functoid[A => B]): Functoid[B] = {
    map2(that)((a, f) => f(a))
  }

  /**
    * Apply a function produced by `this` Functoid to the argument produced by `that` Functoid.
    *
    * Same as
    * {{{
    *   this.map2(that)((f, a) => f(a))
    * }}}
    */
  def ap[B, C](that: Functoid[B])(implicit @unused ev: A <:< (B => C), tag: Tag[C]): Functoid[C] = {
    map2(that)((f, a) => f.asInstanceOf[B => C](a))
  }

  /** Add `B` as an unused dependency of this Functoid */
  def addDependency[B: Tag]: Functoid[A] = addDependency(DIKey[B])
  def addDependency[B: Tag](name: Identifier): Functoid[A] = addDependency(DIKey[B](name))
  def addDependency(key: DIKey): Functoid[A] = addDependencies(key :: Nil)
  def addDependencies(keys: Iterable[DIKey]): Functoid[A] = copy[A](get = get.addUnused(keys))

  @inline private def getRetTag: Tag[A @uncheckedVariance] = Tag(get.ret.cls, get.ret.tag)
}

object Functoid extends FunctoidMacroMethods with FunctoidLifecycleAdapters{

  implicit final class SyntaxMapSame[A](private val functoid: Functoid[A]) extends AnyVal {
    def mapSame(f: A => A): Functoid[A] = functoid.map(f)(functoid.getRetTag)
  }

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

  /** Derive constructor for a concrete class `A` using [[ClassConstructor]] */
  def makeClass[A: ClassConstructor]: Functoid[A] = ClassConstructor[A]

  /**
    * Derive constructor for an abstract class or a trait `A` using [[TraitConstructor]]
    *
    * @see [[https://izumi.7mind.io/distage/basics.html#auto-traits Auto-Traits feature]]
    */
  def makeTrait[A: TraitConstructor]: Functoid[A] = TraitConstructor[A]

  /**
    * Derive constructor for a "factory-like" abstract class or a trait `A` using [[FactoryConstructor]]
    *
    * @see [[https://izumi.7mind.io/distage/basics.html#auto-factories Auto-Factories feature]]
    */
  def makeFactory[A: FactoryConstructor]: Functoid[A] = FactoryConstructor[A]

  /**
    * Derive constructor for a `zio.Has` value `A` using [[HasConstructor]]
    *
    * @see [[https://izumi.7mind.io/distage/basics.html#zio-has-bindings ZIO Has bindings]]
    */
  def makeHas[A: HasConstructor]: Functoid[A] = HasConstructor[A]

  /** Derive constructor for a type `A` using [[AnyConstructor]] */
  def makeAny[A: AnyConstructor]: Functoid[A] = AnyConstructor[A]

  def todoProvider(key: DIKey)(implicit pos: CodePositionMaterializer): Functoid[Nothing] = {
    new Functoid[Nothing](
      Provider.ProviderImpl(
        parameters = Seq.empty,
        ret = key.tpe,
        fun = _ => throw new TODOBindingException(s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.get}!", key, pos),
        providerType = ProviderType.Function,
      )
    )
  }

  def identityKey(key: DIKey): Functoid[?] = {
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

  @inline private[this] def firstParamSymbolInfo(tpe: SafeType): SymbolInfo = {
    SymbolInfo(
      name = "x$1",
      finalResultType = tpe,
      isByName = false,
      wasGeneric = false,
    )
  }



}

trait FunctoidLifecycleAdapters {

  import cats.effect.kernel.{Resource, Sync}
  import izumi.reflect.{Tag, TagK}
  import izumi.functional.lifecycle.Lifecycle
  import zio.*
  import scala.language.implicitConversions

  /**
    * Allows you to bind [[cats.effect.Resource]]-based constructors in `ModuleDef`:
    *
    * Example:
    * {{{
    *   import cats.effect._
    *
    *   val catsResource = Resource.liftF(IO(5))
    *
    *   val module = new distage.ModuleDef {
    *     make[Int].fromResource(catsResource)
    *   }
    * }}}
    *
    * @note binding a cats Resource[F, A] will add a
    *       dependency on `Sync[F]` for your corresponding `F` type
    *       (`Sync[F]` instance will generally be provided automatically via [[izumi.distage.modules.DefaultModule]])
    */
  implicit final def providerFromCats[F[_] : TagK, A](
                                                       resource: => Resource[F, A]
                                                     )(implicit tag: Tag[Lifecycle.FromCats[F, A]]
                                                     ): Functoid[Lifecycle.FromCats[F, A]] = {
    Functoid.identity[Sync[F]].map {
      implicit sync: Sync[F] =>
        Lifecycle.fromCats(resource)(sync)
    }
  }

  /**
    * Allows you to bind [[zio.ZManaged]]-based constructors in `ModuleDef`:
    */
  implicit final def providerFromZIO[R, E, A](
                                               managed: => ZManaged[R, E, A]
                                             )(implicit tag: Tag[Lifecycle.FromZIO[R, E, A]]
                                             ): Functoid[Lifecycle.FromZIO[R, E, A]] = {
    Functoid.lift(Lifecycle.fromZIO(managed))
  }

  /**
    * Allows you to bind [[zio.ZManaged]]-based constructors in `ModuleDef`:
    */
  // workaround for inference issues with `E=Nothing`, scalac error: Couldn't find Tag[FromZIO[Any, E, Clock]] when binding ZManaged[Any, Nothing, Clock]
  implicit final def providerFromZIONothing[R, A](
                                                   managed: => ZManaged[R, Nothing, A]
                                                 )(implicit tag: Tag[Lifecycle.FromZIO[R, Nothing, A]]
                                                 ): Functoid[Lifecycle.FromZIO[R, Nothing, A]] = {
    Functoid.lift(Lifecycle.fromZIO(managed))
  }

  /**
    * Allows you to bind [[zio.ZLayer]]-based constructors in `ModuleDef`:
    */
  implicit final def providerFromZLayerHas1[R, E, A: Tag](
                                                           layer: => ZLayer[R, E, Has[A]]
                                                         )(implicit tag: Tag[Lifecycle.FromZIO[R, E, A]]
                                                         ): Functoid[Lifecycle.FromZIO[R, E, A]] = {
    Functoid.lift(Lifecycle.fromZIO(layer.build.map(_.get[A])))
  }

  /**
    * Allows you to bind [[zio.ZLayer]]-based constructors in `ModuleDef`:
    */
  // workaround for inference issues with `E=Nothing`, scalac error: Couldn't find Tag[FromZIO[Any, E, Clock]] when binding ZManaged[Any, Nothing, Clock]
  implicit final def providerFromZLayerNothingHas1[R, A: Tag](
                                                               layer: => ZLayer[R, Nothing, Has[A]]
                                                             )(implicit tag: Tag[Lifecycle.FromZIO[R, Nothing, A]]
                                                             ): Functoid[Lifecycle.FromZIO[R, Nothing, A]] = {
    Functoid.lift(Lifecycle.fromZIO(layer.build.map(_.get[A])))
  }
}