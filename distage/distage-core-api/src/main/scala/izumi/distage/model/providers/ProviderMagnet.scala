package izumi.distage.model.providers

import izumi.distage.model.exceptions.TODOBindingException
import izumi.distage.model.reflection.macros.ProviderMagnetMacro
import izumi.distage.model.reflection.AssociationP.Parameter
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection._
import izumi.fundamentals.platform.language.{CodePositionMaterializer, unused}
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.reflection.Tags.Tag

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
  * Annotation processing is done by a macro and macros are rarely perfect,
  * Prefer passing an inline lambda such as { x => y } or a method reference such as (method _) or (method(_))
  * Annotation info may be lost ONLY in a few cases detailed below, though:
  *  - If an annotated method has been hidden by an intermediate `val`
  *  - If an `.apply` method of a case class is passed when case class _parameters_ are annotated, not their types
  *
  * As such, prefer annotating parameter types, not parameters: `class X(i: Int @Id("special")) { ... }`
  *
  * When binding a case class to constructor, prefer passing `new X(_)` instead of `X.apply _` because `apply` will
  * not preserve parameter annotations from case class definitions:
  *
  * {{{
  *  case class X(@Id("special") i: Int)
  *
  *  make[X].from(X.apply _) // summons regular Int
  *  make[X].from(new X(_)) // summons special Int
  * }}}
  *
  * HOWEVER, if you annotate the types of parameters instead of their names, `apply` WILL work:
  *
  * {{{
  *   case class X(i: Int @Id("special"))
  *
  *   make[X].from(X.apply _) // summons special Int
  * }}}
  *
  * Using intermediate vals will lose annotations when converting a method into a function value,
  * prefer using annotated method directly as method reference `(method _)`:
  *
  * {{{
  *   def constructorMethod(@Id("special") i: Int): Unit = ()
  *
  *   val constructor = constructorMethod _
  *
  *   make[Unit].from(constructor) // Will summon regular Int, not a "special" Int from DI object graph
  * }}}
  *
  * ProviderMagnet forms an applicative functor via its [[ProviderMagnet.pure]] & [[map2]] methods
  *
  * @see [[izumi.distage.model.reflection.macros.ProviderMagnetMacro]]]
  * @see 'Magnet' in the name refers to the Magnet Pattern: http://spray.io/blog/2012-12-13-the-magnet-pattern/
  */
final case class ProviderMagnet[+A](get: Provider) {
  def map[B: Tag](f: A => B): ProviderMagnet[B] = {
    copy[B](get = get.unsafeMap(SafeType.get[B], (any: Any) => f(any.asInstanceOf[A])))
  }

  def zip[B](that: ProviderMagnet[B]): ProviderMagnet[(A, B)] = {
    implicit val tagA: Tag[A] = this.getRetTag
    implicit val tagB: Tag[B] = that.getRetTag
    tagA.discard() // used for assembling Tag[(A, B)] below
    tagB.discard()
    copy[(A, B)](get = get.unsafeZip(SafeType.get[(A, B)], that.get))
  }

  def map2[B, C: Tag](that: ProviderMagnet[B])(f: (A, B) => C): ProviderMagnet[C] = {
    zip(that).map[C](f.tupled)
  }

  /** Applicative's `ap` method - can be used to chain transformations like `flatMap` */
  def flatAp[B: Tag](that: ProviderMagnet[A => B]): ProviderMagnet[B] = {
    implicit val rTag: Tag[A] = getRetTag
    rTag.discard() // used for assembling Tag[A => B] below
    map2[A => B, B](that) { case (a, f) => f(a) }
  }

  /** Apply a function produced by `this` Provider to the argument produced by `that` Provider */
  def ap[B, C](that: ProviderMagnet[B])(implicit @unused ev: A <:< (B => C), tag: Tag[C]): ProviderMagnet[C] = {
    that.flatAp[C](this.asInstanceOf[ProviderMagnet[B => C]])
  }

  /** Add `B` as an unused dependency of this constructor */
  def addDependency[B: Tag]: ProviderMagnet[A] = addDependency(DIKey.get[B])

  def addDependency(key: DIKey): ProviderMagnet[A] = addDependencies(key :: Nil)

  def addDependencies(keys: Seq[DIKey]): ProviderMagnet[A] = copy[A](get = get.addUnused(keys))

  @inline private def getRetTag: Tag[A @uncheckedVariance] = Tag(get.ret.cls, get.ret.tag)
}

object ProviderMagnet {
  implicit def apply[R](fun: () => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]

  def todoProvider(key: DIKey)(implicit pos: CodePositionMaterializer): ProviderMagnet[Nothing] =
    new ProviderMagnet[Nothing](
      Provider.ProviderImpl(
        parameters = Seq.empty,
        ret = key.tpe,
        fun = _ => throw new TODOBindingException(s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.get}!", key, pos),
        providerType = ProviderType.Function,
      )
    )

  def identity[A: Tag]: ProviderMagnet[A] = identityKey(DIKey.get[A]).asInstanceOf[ProviderMagnet[A]]

  def pure[A: Tag](a: A): ProviderMagnet[A] = lift(a)

  def unit: ProviderMagnet[Unit] = pure(())

  def lift[A: Tag](a: => A): ProviderMagnet[A] = {
    new ProviderMagnet[A](
      Provider.ProviderImpl[A](
        parameters = Seq.empty,
        ret = SafeType.get[A],
        originalFun = () => a,
        fun = (_: Seq[Any]) => a,
        providerType = ProviderType.Function,
      )
    )
  }

  def singleton[A <: Singleton: Tag](a: A): ProviderMagnet[A] = {
    new ProviderMagnet[A](
      Provider.ProviderImpl[A](
        parameters = Seq.empty,
        ret = SafeType.get[A],
        fun = (_: Seq[Any]) => a,
        providerType = ProviderType.Singleton,
      )
    )
  }

  def single[A: Tag, B: Tag](f: A => B): ProviderMagnet[B] = {
    val key = DIKey.get[A]
    val tpe = key.tpe
    val retTpe = SafeType.get[B]
    val symbolInfo = firstParamSymbolInfo(tpe)

    new ProviderMagnet[B](
      Provider.ProviderImpl(
        parameters = Seq(Parameter(symbolInfo, key)),
        ret = retTpe,
        originalFun = f,
        fun = (s: Seq[Any]) => f(s.head.asInstanceOf[A]),
        providerType = ProviderType.Function,
      )
    )
  }

  def identityKey(key: DIKey): ProviderMagnet[_] = {
    val tpe = key.tpe
    val symbolInfo = firstParamSymbolInfo(tpe)

    new ProviderMagnet(
      Provider.ProviderImpl(
        parameters = Seq(Parameter(symbolInfo, key)),
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
