package izumi.distage.model.providers

import izumi.distage.model.definition.Identifier
import izumi.distage.model.reflection.*
import izumi.distage.reflection.macros.FunctoidMacroMethodsTemplate
import izumi.fundamentals.platform.language.Quirks.*
import izumi.reflect.Tag

import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.unused

trait FunctoidTemplate extends FunctoidMacroMethodsTemplate with SimpleFunctoidsTemplate {

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
  case class Functoid[+A](get: Provider) {
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

    /**
      * Add an `@Id` annotation to an unannotated parameter `P`, e.g.
      * for .annotateParameter("x"), transform lambda `(p: P) => x(p)`
      * into `(p: P @Id("x")) => x(p)`
      */
    def annotateParameter[P: Tag](name: Identifier): Functoid[A] = {
      val paramTpe = SafeType.get[P]
      annotateParameterWhen(name) {
        case DIKey.TypeKey(tpe, _) => tpe == paramTpe
        case _: DIKey.IdKey[?] => false
      }
    }
    /** Add an `@Id(name)` annotation to all unannotated parameters */
    def annotateAllParameters(name: Identifier): Functoid[A] = {
      annotateParameterWhen(name) {
        case _: DIKey.TypeKey => true
        case _: DIKey.IdKey[?] => false
      }
    }
    /** Add an `@Id(name)` annotation to all parameters matching `predicate` */
    def annotateParameterWhen(name: Identifier)(predicate: DIKey.BasicKey => Boolean): Functoid[A] = {
      val newProvider = this.get.replaceKeys {
        case k: DIKey.BasicKey if predicate(k) =>
          DIKey.IdKey(k.tpe, name.id, k.mutatorIndex)(name.idContract)
        case k => k
      }
      Functoid(newProvider)
    }

    @inline private def getRetTag: Tag[A @uncheckedVariance] = Tag(get.ret.closestClass, get.ret.tag)
  }

  object Functoid extends FunctoidMacroMethods with SimpleFunctoids {

    implicit final class SyntaxMapSame[A](private val functoid: Functoid[A]) {
      def mapSame(f: A => A): Functoid[A] = functoid.map(f)(functoid.getRetTag)
    }

    def pure[A: Tag](a: A): Functoid[A] = lift(a)

    def unit: Functoid[Unit] = pure(())

  }

}
