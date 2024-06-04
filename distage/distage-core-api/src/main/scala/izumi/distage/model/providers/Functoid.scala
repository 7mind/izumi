package izumi.distage.model.providers

import izumi.distage.model.reflection.*
import izumi.distage.reflection.macros.FunctoidMacroMethods
import izumi.reflect.Tag

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
  * @see [[izumi.distage.reflection.macros.FunctoidMacro]]]
  * @see Functoid is based on the Magnet Pattern: [[http://spray.io/blog/2012-12-13-the-magnet-pattern/]]
  * @see Essentially Functoid is a function-like entity with additional properties, so it's funny name is reasonable enough: [[https://en.wiktionary.org/wiki/-oid#English]]
  */
final case class Functoid[+A](get: Provider) extends AbstractFunctoid[A, Functoid] {
  override protected def create[B](provider: Provider): Functoid[B] = copy(get = provider)
}

object Functoid extends FunctoidMacroMethods with SimpleFunctoids with FunctoidLifecycleAdapters with FunctoidConstructors {

//  implicit final def convert[R, Self[+A] <: AbstractFunctoid[A, Self]](f: Self[R]): Functoid[R] = {
//    new Functoid[R](f.get)
//  }

  implicit final class SyntaxMapSame[A](private val functoid: Functoid[A]) extends AnyVal {
    def mapSame(f: A => A): Functoid[A] = functoid.map(f)(functoid.returnTypeTag)
  }

  def pure[A: Tag](a: A): Functoid[A] = lift(a)

  def unit: Functoid[Unit] = pure(())

}
