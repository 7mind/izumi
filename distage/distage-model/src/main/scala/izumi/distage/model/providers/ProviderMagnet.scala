package izumi.distage.model.providers

import izumi.distage.model.exceptions.TODOBindingException
import izumi.distage.model.reflection.macros.ProviderMagnetMacro
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{Association, DIKey, DependencyContext, Provider, SafeType, SymbolInfo}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.reflection.Tags.Tag

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
  * Function value with annotated signature:
  *
  * {{{
  *   val constructor: (Int @Id("special"), String @Id("special")) => Unit = (_, _) => ()
  *
  *   make[Unit].from(constructor)
  * }}}
  *
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
  * @see [[izumi.distage.model.reflection.macros.ProviderMagnetMacro]]]
  */
final case class ProviderMagnet[+A](get: Provider) {
  def map[B: Tag](f: A => B): ProviderMagnet[B] = {
    copy[B](get = get.unsafeMap(SafeType.get[B], (any: Any) => f(any.asInstanceOf[A])))
  }

  def zip[B: Tag](that: ProviderMagnet[B]): ProviderMagnet[(A, B)] = {
    implicit val rTag: Tag[A] = Tag.unsafeFromSafeType(Tag[B].tpe.mirror)(get.ret)
    rTag.discard() // scalac can't detect usage in TagMacro assembling Tag[(R, B)] below

    copy[(A, B)](get = get.unsafeZip(SafeType.get[(A, B)], that.get))
  }

  def map2[B: Tag, C: Tag](that: ProviderMagnet[B])(f: (A, B) => C): ProviderMagnet[C] = {
    zip(that).map[C](f.tupled)
  }

  /** Add `B` as an unused dependency for this constructor */
  def addDependency[B: Tag]: ProviderMagnet[A] = {
    implicit val rTag: Tag[A] = Tag.unsafeFromSafeType(Tag[B].tpe.mirror)(get.ret)
    map2[B, A](ProviderMagnet.identity[B])((a, _) => a)
  }
}

object ProviderMagnet {
  implicit def apply[R](fun: () => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
  implicit def apply[R](fun: _ => R): ProviderMagnet[R] = macro ProviderMagnetMacro.impl[R]
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

  def todoProvider(key: DIKey)(implicit pos: CodePositionMaterializer): ProviderMagnet[_] =
    new ProviderMagnet[Any](
      Provider.ProviderImpl(
        associations = Seq.empty
        , ret = key.tpe
        , fun = _ => throw new TODOBindingException(
          s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.get}!", key, pos)
      )
    )

  def identity[A: Tag]: ProviderMagnet[A] = {
    val tpe = SafeType.get[A]
    val key = DIKey.get[A]
    val debugInfo = DependencyContext.ConstructorParameterContext(tpe, SymbolInfo.Static("x$1", tpe, Nil, tpe, isByName = false, wasGeneric = false))

    new ProviderMagnet[A](
      Provider.ProviderImpl[A](
        associations = Seq(Association.Parameter(debugInfo, "x$1", tpe, key, isByName = false, wasGeneric = false))
      , fun = (_: Seq[Any]).head.asInstanceOf[A]
      )
    )
  }

  def pure[A: Tag](a: A): ProviderMagnet[A] = {
    lift(a)
  }

  def lift[A: Tag](a: => A): ProviderMagnet[A] = {
    new ProviderMagnet[A](
      Provider.ProviderImpl[A](
        associations = Seq.empty
        , fun = (_: Seq[Any]) => a
      )
    )
  }

  def generateUnsafeWeakSafeTypes[R](fun: Any): ProviderMagnet[R] = macro ProviderMagnetMacro.generateUnsafeWeakSafeTypes[R]

}
