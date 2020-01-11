package izumi.fundamentals.reflection

import izumi.fundamentals.reflection.macrortti.{LTag, LightTypeTag}

import scala.annotation.implicitNotFound
import scala.language.experimental.macros

object Tags {

  trait AnyTag {
    def tag: LightTypeTag

    /**
      * Closest class found for the type or for a LUB of all intersection
      * members in case of an intersection type.
      *
      * A Scala type may not have an associated JVM class, as such
      * this class may not be sufficient to create instances of `T`
      *
      * Only if `tag.hasPreciseClass` returns true
      * it may be safe to reflect on `closestClass`
      */
    def closestClass: Class[_]

    final def hasPreciseClass: Boolean = {
      tag.shortName == closestClass.getSimpleName
    }
  }

  /**
    * Like [[scala.reflect.api.TypeTags.TypeTag]], but supports higher-kinded type tags via `TagK` type class.
    *
    * In context of DI this lets you define modules parameterized by higher-kinded type parameters.
    * This is especially helpful for applying [[https://www.beyondthelines.net/programming/introduction-to-tagless-final/ `tagless final` style]]
    *
    * Example:
    * {{{
    * class MyModule[F[_]: Monad: TagK] {
    *   make[MyService[F]]
    *   make[F[Int]].named("lucky-number").from(Monad[F].pure(7))
    * }
    * }}}
    *
    * Without a `TagK` constraint above, this example would fail with `no TypeTag available for MyService[F]` error
    *
    * Currently some limitations apply as to when a `Tag` will be correctly constructed:
    *   * Type Parameters do not yet resolve inside structural refinements, e.g. T in {{{ Tag[{ def x: T}] }}}
    *   * TagK* does not resolve for constructors with bounded parameters, e.g. S in {{{ class Abc[S <: String]; TagK[Abc] }}}
    *     (You can still have a bound in partial application: e.g. {{{ class Abc[S <: String, A]; TagK[Abc["hi", ?]] }}}
    *   * Further details at [[https://github.com/7mind/izumi/issues/374]]
    *
    * @see "Lightweight Scala Reflection and why Dotty needs TypeTags reimplemented" https://blog.7mind.io/lightweight-reflection.html
    */
  @implicitNotFound("could not find implicit value for Tag[${T}]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in ${T}? e.g. def x[T: Tag, F[_]: TagK] = ...")
  trait Tag[T] extends AnyTag {
    def tag: LightTypeTag
    def closestClass: Class[_]

    override final def toString: String = s"Tag[$tag]"
  }

  object Tag {

    /**
      * Use `Tag.auto.T[TYPE_PARAM]` syntax to summon a `Tag` for a type parameter of any kind:
      *
      * {{{
      *   def module1[F[_]: Tag.auto.T] = new ModuleDef {
      *     ...
      *   }
      *
      *   def module2[F[_, _]: Tag.auto.T] = new ModuleDef {
      *     ...
      *   }
      * }}}
      *
      * {{{
      *   def y[K[_[_, _], _[_], _[_[_], _, _, _]](implicit ev: Tag.auto.T[K]): Tag.auto.T[K] = ev
      * }}}
      *
      * {{{
      *   def x[K[_[_, _], _[_], _[_[_], _, _, _]: Tag.auto.T]: Tag.auto.T[K] = implicitly[Tag.auto.T[K]]
      * }}}
      *
      **/
    def auto: Any = macro TagLambdaMacro.lambdaImpl

    @inline def apply[T: Tag]: Tag[T] = implicitly

    def apply[T](cls: Class[_], tag0: LightTypeTag): Tag[T] = {
      new Tag[T] {
        override val tag: LightTypeTag = tag0
        override val closestClass: Class[_] = cls
      }
    }

    /**
      * Create a Tag of a type formed by applying the type in `tag` to `args`.
      *
      * Example:
      * {{{
      *   implicit def tagFromTagTAKA[T[_, _[_], _], K[_]: TagK, A0: Tag, A1: Tag](implicit t: LTagK3[T]): Tag[T[A0, K, A1]] =
      *     Tag.appliedTag(t.tag, List(Tag[A0].tag, TagK[K].tag, Tag[A1].tag))
      * }}}
      **/
    def appliedTag[R](tag: HKTag[_], args: List[LightTypeTag]): Tag[R] = {
      Tag(tag.closestClass, tag.tag.combine(args: _*))
    }

    /**
      * Create a Tag of a type formed from an `intersection` of types (A with B) with a structural refinement taken from `structType`
      *
      * `structType` is assumed to be a weak type of the entire type, e.g.
      * {{{
      *   Tag[A with B {def abc: Unit}] == refinedTag(List(LTag[A].tag, LTag[B].tag), LTag.Weak[A with B { def abc: Unit }].tag)
      * }}}
      **/
    def refinedTag[R](lubClass: Class[_], intersection: List[LightTypeTag], structType: LightTypeTag): Tag[R] = {
      Tag(lubClass, LightTypeTag.refinedType(intersection, structType))
    }

    implicit final def tagFromTagMacro[T]: Tag[T] = macro TagMacro.makeTag[T]
  }

  /**
    * Internal unsafe API representing a poly-kinded, higher-kinded type tag.
    *
    * To create a Tag* implicit for an arbitrary kind use the following syntax:
    *
    * {{{
    *   type TagK5[K[_, _, _, _, _]] = HKTag[ { type Arg[A, B, C, D, E] = K[A, B, C, D, E] } ]
    * }}}
    *
    * As an argument to HKTag, you should specify the type variables your type parameter will take and apply them to it, in order.
    *
    * {{{
    *   type TagFGC[K[_[_, _], _[_], _[_[_], _, _, _]] = HKTag[ { type Arg[A[_, _], B[_], C[_[_], _, _, _]] = K[A, B, C] } ]
    * }}}
    *
    * A convenience macro `Tag.auto.T` is available to automatically create a type lambda for a type of any kind:
    *
    * {{{
    *   def x[K[_[_, _], _[_], _[_[_], _, _, _]: Tag.auto.T]: Tag.auto.T[K] = implicitly[Tag.auto.T[K]]
    * }}}
    */
  trait HKTag[T] extends AnyTag {
    /** Internal `LightTypeTag` holding the `typeConstructor` of type `T` */
    def tag: LightTypeTag
    def closestClass: Class[_]

    override final def toString: String = s"HKTag[$tag]"
  }

  object HKTag {
    def apply[T](cls: Class[_], lightTypeTag: LightTypeTag): HKTag[T] = new HKTag[T] {
      override val tag: LightTypeTag = lightTypeTag
      override val closestClass: Class[_] = cls
    }

    def appliedTagNonPos[R](tag: HKTag[_], args: List[Option[LightTypeTag]]): HKTag[R] = {
      HKTag(tag.closestClass, tag.tag.combineNonPos(args: _*))
    }

    def appliedTagNonPosAux[R](cls: Class[_], ctor: LightTypeTag, args: List[Option[LightTypeTag]]): HKTag[R] = {
      HKTag(cls, ctor.combineNonPos(args: _*))
    }

    @inline implicit final def hktagFromTagMacro[T](implicit materializer: HKTagMaterializer[T]): HKTag[T] = materializer.value
  }

  /**
    * Force eager expansion for all recursive implicit searches inside TagMacro
    * by introducing a proxy implicit to display better error messages
    *
    * @see test ResourceEffectBindingsTest."Display tag macro stack trace when ResourceTag is not found"
    */
  final class HKTagMaterializer[T](val value: HKTag[T]) extends AnyVal
  object HKTagMaterializer {
    // FIXME: TagK construction macro
    implicit def materializeHKTag[T]: HKTagMaterializer[T] = macro TagMacro.makeHKTagMaterializer[T]
  }


  /**
    * `TagK` is like a [[scala.reflect.api.TypeTags.TypeTag]] but for higher-kinded types.
    *
    * Example:
    * {{{
    * def containerTypesEqual[F[_]: TagK, K[_]: TagK]): Boolean = TagK[F].tag.tpe =:= TagK[K].tag.tpe
    *
    * containerTypesEqual[Set, collection.immutable.Set] == true
    * containerTypesEqual[Array, List] == false
    * }}}
    */
  type TagK[K[_]] = HKTag[{ type Arg[A] = K[A] }]
  type TagKK[K[_, _]] = HKTag[{ type Arg[A, B] = K[A, B] }]
  type TagK3[K[_, _, _]] = HKTag[{ type Arg[A, B, C] = K[A, B, C]}]

  type TagT[K[_[_]]] = HKTag[{ type Arg[A[_]] = K[A]}]
  type TagTK[K[_[_], _]] = HKTag[{ type Arg[A[_], B] = K[A, B] }]
  type TagTKK[K[_[_], _, _]] = HKTag[{ type  Arg[A[_], B, C] = K[A, B, C] }]
  type TagTK3[K[_[_], _, _, _]] = HKTag[{ type Arg[A[_], B, C, D] = K[A, B, C, D] }]

  object TagK {
    /**
      * Construct a type tag for a higher-kinded type `K[_]`
      *
      * Example:
      * {{{
      *     TagK[Option]
      * }}}
      **/
    @inline def apply[K[_]: TagK]: TagK[K] = implicitly
  }

  object TagKK {
    @inline def apply[K[_, _]: TagKK]: TagKK[K] = implicitly
  }

  object TagK3 {
    @inline def apply[K[_, _, _]: TagK3]: TagK3[K] = implicitly
  }

  object TagT {
    @inline def apply[K[_[_]]: TagT]: TagT[K] = implicitly
  }

  object TagTK {
    @inline def apply[K[_[_], _]: TagTK]: TagTK[K] = implicitly
  }

  object TagTKK {
    @inline def apply[K[_[_], _, _]: TagTKK]: TagTKK[K] = implicitly
  }

  object TagTK3 {
    @inline def apply[K[_[_], _, _, _]: TagTK3]: TagTK3[K] = implicitly
  }

// TODO
//  type TagKUBound[U, K[_ <: U]] = HKTag[{ type Arg[A <: U] = K[A] }]
//  object TagKUBound {
//    def apply[U, K[_ <: U]](implicit ev: TagKUBound[U, K]): TagKUBound[U, K] = implicitly
//  }

  // Workaround needed specifically to support generic methods in factories, see `GenericAssistedFactory` and related tests
  //
  // We need to construct a SafeType signature for a generic method, but generic parameters have no type tags
  // So we resort to weak type parameters and pointer equality
  trait WeakTag[T] extends AnyTag {
    def tag: LightTypeTag
    def closestClass: Class[_]

    override final def toString: String = s"WeakTag[$tag]"
  }

  object WeakTag extends WeakTagInstances1 {
    def apply[T: WeakTag]: WeakTag[T] = implicitly

    def apply[T](cls: Class[_], l: LightTypeTag): WeakTag[T] = {
      new WeakTag[T] {
        override val tag: LightTypeTag = l
        override val closestClass: Class[_] = cls
      }
    }

    implicit def weakTagFromTag[T: Tag]: WeakTag[T] = WeakTag(Tag[T].closestClass, Tag[T].tag)
  }
  trait WeakTagInstances1 {
    implicit def weakTagFromWeakTypeTag[T](implicit l: LTag.Weak[T]): WeakTag[T] = WeakTag(classOf[Any], l.tag)
  }

}
