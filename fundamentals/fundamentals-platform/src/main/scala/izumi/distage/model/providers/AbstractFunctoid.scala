package izumi.distage.model.providers

import izumi.distage.model.definition.Identifier
import izumi.distage.model.reflection.{DIKey, Provider, SafeType}
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.reflect.Tag

import scala.annotation.unchecked.uncheckedVariance
import scala.annotation.unused

trait AbstractFunctoid[+A, Self[+K] <: AbstractFunctoid[K, Self]] {

  def get: Provider

  protected def create[B](provider: Provider): Self[B]

  def map[B: Tag](f: A => B): Self[B] = {
    create[B](get.unsafeMap(SafeType.get[B], (any: Any) => f(any.asInstanceOf[A])))
  }

  def zip[B](that: Self[B]): Self[(A, B)] = {
    implicit val tagA: Tag[A] = this.returnTypeTag
    implicit val tagB: Tag[B] = that.returnTypeTag
    tagA.discard() // used for assembling Tag[(A, B)] below
    tagB.discard()
    create[(A, B)](get.unsafeZip(SafeType.get[(A, B)], that.get))
  }

  def map2[B, C: Tag](that: Self[B])(f: (A, B) => C): Self[C] = {
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
  def flatAp[B: Tag](that: Self[A => B]): Self[B] = {
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
  def ap[B, C](that: Self[B])(implicit @unused ev: A <:< (B => C), tag: Tag[C]): Self[C] = {
    map2(that)((f, a) => f.asInstanceOf[B => C](a))
  }

  /** Add `B` as an unused dependency of this Functoid */
  def addDependency[B: Tag]: Self[A] = addDependency(DIKey[B])
  def addDependency[B: Tag](name: Identifier): Self[A] = addDependency(DIKey[B](name))
  def addDependency(key: DIKey): Self[A] = addDependencies(key :: Nil)
  def addDependencies(keys: Iterable[DIKey]): Self[A] = create[A](get.addUnused(keys))

  /**
    * Add an `@Id` annotation to an unannotated parameter `P`, e.g.
    * for .annotateParameter("x"), transform lambda `(p: P) => x(p)`
    * into `(p: P @Id("x")) => x(p)`
    */
  def annotateParameter[P: Tag](name: Identifier): Self[A] = {
    val paramTpe = SafeType.get[P]
    annotateParameterWhen(name) {
      case DIKey.TypeKey(tpe, _) => tpe == paramTpe
      case _: DIKey.IdKey[?] => false
    }
  }
  /** Add an `@Id(name)` annotation to all unannotated parameters */
  def annotateAllParameters(name: Identifier): Self[A] = {
    annotateParameterWhen(name) {
      case _: DIKey.TypeKey => true
      case _: DIKey.IdKey[?] => false
    }
  }
  /** Add an `@Id(name)` annotation to all parameters matching `predicate` */
  def annotateParameterWhen(name: Identifier)(predicate: DIKey.BasicKey => Boolean): Self[A] = {
    val newProvider = this.get.replaceKeys {
      case k: DIKey.BasicKey if predicate(k) =>
        DIKey.IdKey(k.tpe, name.id, k.mutatorIndex)(name.idContract)
      case k => k
    }
    create[A](newProvider)
  }

  def returnTypeTag: Tag[A @uncheckedVariance] = Tag(get.ret.closestClass, get.ret.tag)
}

final case class BaseFunctoid[+A](get: Provider) extends AbstractFunctoid[A, BaseFunctoid] {
  override protected def create[B](provider: Provider): BaseFunctoid[B] = copy(get = provider)
}
