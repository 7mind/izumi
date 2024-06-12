package izumi.distage.model.providers

import izumi.distage.model.definition.Identifier
import izumi.distage.model.reflection.{DIKey, Provider, SafeType}
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.reflect.Tag

import scala.annotation.unchecked.uncheckedVariance

trait AbstractFunctoid[+A, Ftoid[+X] <: AbstractFunctoid[X, Ftoid]] {

  def get: Provider

  protected def create[B](provider: Provider): Ftoid[B]

  def map[B: Tag](f: A => B): Ftoid[B] = {
    create[B](get.unsafeMap(SafeType.get[B], (any: Any) => f(any.asInstanceOf[A])))
  }

  def zip[B](that: Ftoid[B]): Ftoid[(A, B)] = {
    implicit val tagA: Tag[A] = this.returnTypeTag
    implicit val tagB: Tag[B] = that.returnTypeTag
    tagA.discard() // used for assembling Tag[(A, B)] below
    tagB.discard()
    create[(A, B)](get.unsafeZip(SafeType.get[(A, B)], that.get))
  }

  def map2[B, C: Tag](that: Ftoid[B])(f: (A, B) => C): Ftoid[C] = {
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
  def flatAp[B: Tag](that: Ftoid[A => B]): Ftoid[B] = {
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
  def ap[B, C](that: Ftoid[B])(implicit ev: A <:< (B => C), tag: Tag[C]): Ftoid[C] = {
    map2[B, C](that)((f, a) => ev(f)(a))(using tag)
  }

  /** Add `B` as an unused dependency of this Functoid */
  def addDependency[B: Tag]: Ftoid[A] = addDependency(DIKey[B])
  def addDependency[B: Tag](name: Identifier): Ftoid[A] = addDependency(DIKey[B](name))
  def addDependency(key: DIKey): Ftoid[A] = addDependencies(key :: Nil)
  def addDependencies(keys: Iterable[DIKey]): Ftoid[A] = create[A](get.addUnused(keys))

  /**
    * Add an `@Id` annotation to an unannotated parameter `P`, e.g.
    * for .annotateParameter("x"), transform lambda `(p: P) => x(p)`
    * into `(p: P @Id("x")) => x(p)`
    */
  def annotateParameter[P: Tag](name: Identifier): Ftoid[A] = {
    val paramTpe = SafeType.get[P]
    annotateParameterWhen(name) {
      case DIKey.TypeKey(tpe, _) => tpe == paramTpe
      case _: DIKey.IdKey[?] => false
    }
  }
  /** Add an `@Id(name)` annotation to all unannotated parameters */
  def annotateAllParameters(name: Identifier): Ftoid[A] = {
    annotateParameterWhen(name) {
      case _: DIKey.TypeKey => true
      case _: DIKey.IdKey[?] => false
    }
  }
  /** Add an `@Id(name)` annotation to all parameters matching `predicate` */
  def annotateParameterWhen(name: Identifier)(predicate: DIKey.BasicKey => Boolean): Ftoid[A] = {
    val newProvider = this.get.replaceKeys {
      case k: DIKey.BasicKey if predicate(k) =>
        DIKey.IdKey(k.tpe, name.id, k.mutatorIndex)(name.idContract)
      case k => k
    }
    create[A](newProvider)
  }

  private[distage] def returnTypeTag: Tag[A @uncheckedVariance] = Tag(get.ret.closestClass, get.ret.tag)
}
