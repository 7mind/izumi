package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.fundamentals.reflection.SingletonUniverse

import scala.language.higherKinds
import scala.reflect.api
import scala.reflect.api.TypeCreator

trait WithDITypeTags {
  this: DIUniverseBase =>

  import u._

  /**
  * Like [[scala.reflect.api.TypeTags#TypeTag]], but supports higher-kinded type tags via `TagK` type class.
  *
  * In context of DI this allows you to define a module parameterized by a generic higher-kinded type parameter.
  * This is especially helpful when coding in a [[https://www.beyondthelines.net/programming/introduction-to-tagless-final/ `tagless final` style]]
  *
  * Example:
  * {{{
  * class MyModule[F[_]: Monad: TagK] {
  *   make[MyService[F]]
  *   make[F[Int]].named("lucky-number").from(Monad[F].pure(7))
  * }
  * }}}
  *
  * Without a `TagK` constraint above, this example would fail with `no TypeTag available for MyService[F]`
  */
  trait Tag[T] {
    def tag: TypeTag[T]

    override def toString: String = s"Tag[${tag.tpe}]"
  }

  object Tag extends TagInstances0 {
    def apply[T: Tag]: Tag[T] = implicitly

    def apply[T](t: TypeTag[T]): Tag[T] =
      new Tag[T] {
        override val tag: TypeTag[T] = t
      }

    /**
    * Helper for creating your own instances for type shapes that aren't supported by default instances
    * (most shapes in `cats` and `stdlib` are supported, but you may need this if you use `scalaz`)
    *
    * Example:
    * {{{
    * implicit def tagFromTagTAKA[T[_, _[_], _], K[_]: TagK, A0: Tag, A1: Tag](implicit t: TypeTag[T[Nothing, Nothing, Nothing]): Tag[T[A0, K, A1]] =
    *   Tag.appliedTag(t, List(Tag[A0].tag, TagK[K].tag, Tag[A1].tag))
    * }}}
    **/
    def appliedTag[R](tag: WeakTypeTag[_], args: List[TypeTag[_]]): Tag[R] = {
      val appliedTypeCreator = new TypeCreator {
        override def apply[U <: SingletonUniverse](m: api.Mirror[U]): U#Type =
          m.universe.appliedType(tag.migrate(m).tpe.typeConstructor, args.map(_.migrate(m).tpe))
      }
      Tag(TypeTag[R](tag.mirror, appliedTypeCreator))
    }

    def mergeArgs[R](tag: WeakTypeTag[_], args: List[Option[TypeTag[_]]]): Tag[R] = {
      val appliedTypeCreator = new TypeCreator {
        override def apply[U <: SingletonUniverse](m: api.Mirror[U]): U#Type = {
          val tpe = tag.migrate(m).tpe

          val mergedArgs = args.zipWithIndex.map {
            case (Some(t), _) => t.migrate(m).tpe
            case (None, i) => tpe.typeArgs(i)
          }

          m.universe.appliedType(tpe.typeConstructor, mergedArgs)
        }
      }
      Tag(TypeTag[R](tag.mirror, appliedTypeCreator))
    }

  }

  trait TagInstances0 extends TagInstances1 {
    // `Any` is apparently kind polymorphic (only inside the compiler, you can't use that ._.), which breaks other instances, so it's handled separately.
    implicit val tagAny: Tag[Any] =
      new Tag[Any] {
        override val tag: TypeTag[Any] = typeTag[Any]
      }

    // hmm, it works, but not always...
    implicit val tagNothing: Tag[Nothing] =
      new Tag[Nothing] {
        override val tag: TypeTag[Nothing] = typeTag[Nothing]
      }
  }

  trait TagInstances1 extends TagInstances2 {

    implicit def tagFromTypeTagA[A: TypeTag]: Tag[A] = Tag(typeTag[A])

    implicit def tagFromTypeTagKA[K[_], A](implicit t: TypeTag[K[A]]): Tag[K[A]] = Tag(t)

    implicit def tagFromTypeTagKAA[K[_, _], A0, A1](implicit t: TypeTag[K[A0, A1]]): Tag[K[A0, A1]] = Tag(t)

    implicit def tagFromTypeTagKAAA[K[_, _, _], A0, A1, A2](implicit t: TypeTag[K[A0, A1, A2]]): Tag[K[A0, A1, A2]] = Tag(t)

    implicit def tagFromTypeTagKAAAA[K[_, _, _, _], A0, A1, A2, A3](implicit t: TypeTag[K[A0, A1, A2, A3]]): Tag[K[A0, A1, A2, A3]] = Tag(t)

    implicit def tagFromTypeTagKAAAAA[K[_, _, _, _, _], A0, A1, A2, A3, A4](implicit t: TypeTag[K[A0, A1, A2, A3, A4]]): Tag[K[A0, A1, A2, A3, A4]] = Tag(t)

    implicit def tagFromTypeTagTK[T[_[_]], K[_]](implicit t: TypeTag[T[K]]): Tag[T[K]] = Tag(t)

    implicit def tagFromTypeTagTKA[T[_[_], _], K[_], A](implicit t: TypeTag[T[K, A]]): Tag[T[K, A]] = Tag(t)

    implicit def tagFromTypeTagTKAA[T[_[_], _, _], K[_], A0, A1](implicit t: TypeTag[T[K, A0, A1]]): Tag[T[K, A0, A1]] = Tag(t)

    implicit def tagFromTypeTagTKAAA[T[_[_], _, _, _], K[_], A0, A1, A2](implicit t: TypeTag[T[K, A0, A1, A2]]): Tag[T[K, A0, A1, A2]] = Tag(t)

    implicit def tagFromTypeTagTKAAAA[T[_[_], _, _, _, _], K[_], A0, A1, A2, A3](implicit t: TypeTag[T[K, A0, A1, A2, A3]]): Tag[T[K, A0, A1, A2, A3]] = Tag(t)

    implicit def tagFromTypeTagTKAAAAA[T[_[_], _, _, _, _, _], K[_], A0, A1, A2, A3, A4](implicit t: TypeTag[T[K, A0, A1, A2, A3, A4]]): Tag[T[K, A0, A1, A2, A3, A4]] = Tag(t)

    // TODO: wtf? Blasted thing creates a type lambda and chooses tagFromTypeTagTK instead of the most fitting kind! Lambda[A[_] => TestClassFG[Either, A]][Option]
//    implicit def tagFromTypeTagKK2K[K[_[_, _], _[_]], K2[+_, +_]: TagKK, K1[_]: TagK](implicit t: TypeTag[K[K2, K1]]): Tag[K[K2, K1]] = Tag(t)

    implicit def tagFromTypeTagKK2A[K[_[_, _], _], K2[_, _], A](implicit t: TypeTag[K[K2, A]]): Tag[K[K2, A]] = Tag(t)

    implicit def tagFromTypeTagKK2[K[_[_, _]], K2[_, _]: TagKK](implicit t: TypeTag[K[K2]]): Tag[K[K2]] = Tag(t)

  }

  trait TagInstances2 extends TagInstances3 {

    implicit def tagFromTagTAAK[T[_, _, _[_]], A0: Tag, A1: Tag, K[_]: TagK](implicit t: TypeTag[T[Nothing, Nothing, Nothing]]): Tag[T[A0, A1, K]] =
      Tag.appliedTag(t, List(Tag[A0].tag, Tag[A1].tag, TagK[K].tag))

  }

  trait TagInstances3 extends TagInstances4 {

    implicit def tagFromTagTAK[T[_, _[_]], A: Tag, K[_]: TagK](implicit t: TypeTag[T[Nothing, Nothing]]): Tag[T[A, K]] =
      Tag.appliedTag(t, List(Tag[A].tag, TagK[K].tag))

    //
    implicit def tagFromTagKK2A[K[_[_, _], _], K2[_, _]: TagKK, A: Tag](implicit t: TypeTag[K[Nothing, Nothing]]): Tag[K[K2, A]] =
      Tag.appliedTag(t, List(TagKK[K2].tag, Tag[A].tag))

    implicit def tagFromTagKK2K[K[_[_, _], _[_]], K2[_, _]: TagKK, K1[_]: TagK](implicit t: TypeTag[K[Nothing, Nothing]]): Tag[K[K2, K1]] =
      Tag.appliedTag(t, List(TagKK[K2].tag, TagK[K1].tag))

    implicit def tagFromTagKK2[K[_[_, _]], K2[_, _]: TagKK](implicit t: TypeTag[K[Nothing]]): Tag[K[K2]] =
      Tag.appliedTag(t, List(TagKK[K2].tag))

  }

  trait TagInstances4  {

    implicit def tagFromTagKA[K[_]: TagK, A: Tag]: Tag[K[A]] =
      Tag.appliedTag(TagK[K].tag, List(Tag[A].tag))

    implicit def tagFromTagKAA[K[_, _], A0: Tag, A1: Tag](implicit t: TypeTag[K[Nothing, Nothing]]): Tag[K[A0, A1]] =
      Tag.appliedTag(t, List(Tag[A0].tag, Tag[A1].tag))

    implicit def tagFromTagKAAA[K[_, _, _], A0: Tag, A1: Tag, A2: Tag](implicit t: TypeTag[K[Nothing, Nothing, Nothing]]): Tag[K[A0, A1, A2]] =
      Tag.appliedTag(t, List(Tag[A0].tag, Tag[A1].tag, Tag[A2].tag))

    implicit def tagFromTagKAAAA[K[_, _, _, _], A0: Tag, A1: Tag, A2: Tag, A3: Tag](implicit t: TypeTag[K[Nothing, Nothing, Nothing, Nothing]]): Tag[K[A0, A1, A2, A3]] =
      Tag.appliedTag(t, List(Tag[A0].tag, Tag[A1].tag, Tag[A2].tag, Tag[A3].tag))

    implicit def tagFromTagKAAAAA[K[_, _, _, _, _], A0: Tag, A1: Tag, A2: Tag, A3: Tag, A4: Tag](implicit t: TypeTag[K[Nothing, Nothing, Nothing, Nothing, Nothing]]): Tag[K[A0, A1, A2, A3, A4]] =
      Tag.appliedTag(t, List(Tag[A0].tag, Tag[A1].tag, Tag[A2].tag, Tag[A3].tag, Tag[A4].tag))

    implicit def tagFromTagTK[T[_[_]], K[_]: TagK](implicit t: TypeTag[T[Nothing]]): Tag[T[K]] =
      Tag.appliedTag(t, List(TagK[K].tag))

    implicit def tagFromTagTKA[T[_[_], _], K[_]: TagK, A: Tag](implicit t: TypeTag[T[Nothing, Nothing]]): Tag[T[K, A]] =
      Tag.appliedTag(t, List(TagK[K].tag, Tag[A].tag))

    implicit def tagFromTagTKAA[T[_[_], _, _], K[_]: TagK, A0: Tag, A1: Tag](implicit t: TypeTag[T[Nothing, Nothing, Nothing]]): Tag[T[K, A0, A1]] =
      Tag.appliedTag(t, List(TagK[K].tag, Tag[A0].tag, Tag[A1].tag))

    implicit def tagFromTagTKAAA[T[_[_], _, _, _], K[_]: TagK, A0: Tag, A1: Tag, A2: Tag](implicit t: TypeTag[T[Nothing, Nothing, Nothing, Nothing]]): Tag[T[K, A0, A1, A2]] =
      Tag.appliedTag(t, List(TagK[K].tag, Tag[A0].tag, Tag[A1].tag, Tag[A2].tag))

    implicit def tagFromTagTKAAAA[T[_[_], _, _, _, _], K[_]: TagK, A0: Tag, A1: Tag, A2: Tag, A3: Tag](implicit t: TypeTag[T[Nothing, Nothing, Nothing, Nothing, Nothing]]): Tag[T[K, A0, A1, A2, A3]] =
      Tag.appliedTag(t, List(TagK[K].tag, Tag[A0].tag, Tag[A1].tag, Tag[A2].tag, Tag[A3].tag))

    implicit def tagFromTagTKAAAAA[T[_[_], _, _, _, _, _], K[_]: TagK, A0: Tag, A1: Tag, A2: Tag, A3: Tag, A4: Tag](implicit t: TypeTag[T[Nothing, Nothing, Nothing, Nothing, Nothing, Nothing]]): Tag[T[K, A0, A1, A2, A3, A4]] =
      Tag.appliedTag(t, List(TagK[K].tag, Tag[A0].tag, Tag[A1].tag, Tag[A2].tag, Tag[A3].tag, Tag[A4].tag))

    implicit def tagFromTagTKKA[T[_[_], _[_], _], K1[_]: TagK, K2[_]: TagK, A: Tag](implicit t: TypeTag[T[Nothing, Nothing, Nothing]]): Tag[T[K1, K2, A]] =
      Tag.appliedTag(t, List(TagK[K1].tag, TagK[K2].tag, Tag[A].tag))

    implicit def tagFromTagTAKK[T[_, _[_], _[_]], A: Tag, K1[_]: TagK, K2[_]: TagK](implicit t: TypeTag[T[Nothing, Nothing, Nothing]]): Tag[T[A, K1, K2]] =
      Tag.appliedTag(t, List(Tag[A].tag, TagK[K1].tag, TagK[K2].tag))
  }

  /**
  * `TagK` is a [[scala.reflect.api.TypeTags#TypeTag]] for higher-kinded types.
  *
  * Example:
  * {{{
  * def containerTypesEqual[F[_]: TagK, K[_]: TagK](containerF: F[_], containerK: K[_]): Boolean =
  *   TagK[F].tag =:= TagK[K].tag
  * }}}
  */
  trait TagK[K[_]] {

    /**
    * Internal `TypeTag` holding the `typeConstructor` of type `K`
    *
    * You probably want to use `apply` method to replace a `TypeTag` of T[F] with T[K] instead.
    **/
    def tag: TypeTag[_]

    /**
    * Create a [[scala.reflect.api.TypeTags#TypeTag]] for `K[T]` by applying `K[_]` to `T`
    *
    * Example:
    * {{{
    *     TagK[List].apply[Int]
    * }}}
    */
    final def apply[T](implicit tag: Tag[T]): Tag[K[T]] =
      Tag.appliedTag(this.tag, List(tag.tag))

    override final def toString: String = s"TagK[${tag.tpe}]"
  }

  object TagK extends TagKInstances {
    /**
    * Construct a type tag for a higher-kinded type `K`
    *
    * Example:
    * {{{
    *     TagK[Option]
    * }}}
    **/
    def apply[K[_] : TagK]: TagK[K] = implicitly
  }

  trait TagKInstances {
    implicit def tagKFromTypeTag[K[_]](implicit t: TypeTag[K[Nothing]]): TagK[K] =
      new TagK[K] {
        override val tag: TypeTag[_] = {
          val ctorCreator = new TypeCreator {
            override def apply[U <: SingletonUniverse](m: api.Mirror[U]): U#Type =
              t.migrate(m).tpe match {
                case r if r.typeArgs.length == 1 =>
                  r.typeConstructor
                case r =>
                  // create a type lambda preserving embedded arguments
                  // i.e. OptionT[List, ?] === [A => OptionT[List, A]]
                  def newTypeParam[A]: m.universe.Type = m.universe.weakTypeOf[A]

                  val freshParam = newTypeParam
                  val appliedRes = m.universe.appliedType(r, r.typeArgs.dropRight(1) ++ List(freshParam))

                  m.universe.internal.polyType(List(freshParam.typeSymbol), appliedRes)
              }
          }
          TypeTag(t.mirror, ctorCreator)
        }
      }
  }

  trait TagKK[F[_, _]] {
    def tag: TypeTag[_]

    override def toString: String = s"TagKK[${tag.tpe}]"
  }

  object TagKK {
    def apply[F[_, _]: TagKK]: TagKK[F] = implicitly

    implicit def tagKKFromTypeTag[F[_, _]](implicit t: TypeTag[F[Nothing, Nothing]]): TagKK[F] =
      new TagKK[F] {
        override val tag: TypeTag[_] = {
          val ctorCreator = new TypeCreator {
            override def apply[U <: SingletonUniverse](m: api.Mirror[U]): U#Type =
              t.migrate(m).tpe match {
                case r if r.typeArgs.length == 2 =>
                  r.typeConstructor
                case r =>
                  // create a type lambda preserving embedded arguments
                  // i.e. OptionT[List, ?] === [A => OptionT[List, A]]
                  def newTypeParam[A]: m.universe.Type = m.universe.weakTypeOf[A]

                  val freshParam1 = newTypeParam
                  val freshParam2 = newTypeParam
                  val appliedRes = m.universe.appliedType(r, r.typeArgs.dropRight(2) ++ List(freshParam1, freshParam2))

                  m.universe.internal.polyType(List(freshParam1.typeSymbol, freshParam2.typeSymbol), appliedRes)
              }
          }
          TypeTag(t.mirror, ctorCreator)
        }
      }
  }

  implicit class WeakTypeTagMigrate[T](val weakTypeTag: WeakTypeTag[T]) {
    def migrate[U <: SingletonUniverse](m: api.Mirror[U]): m.universe.WeakTypeTag[T] =
      weakTypeTag.in(m).asInstanceOf[m.universe.WeakTypeTag[T]]
  }

  // Workaround needed specifically to support generic methods in factories, see `GenericAssistedFactory` and related tests
  //
  // We need to construct a SafeType signature for a generic method, but generic parameters have no type tags
  // So we resort to weak type parameters and pointer equality
  trait WeakTag[T] {
    def tag: WeakTypeTag[T]

    override final def toString: String = s"WeakTag[${tag.tpe}]"
  }

  object WeakTag extends WeakTagInstances0 {
    def apply[T: WeakTag]: WeakTag[T] = implicitly

    def apply[T](t: WeakTypeTag[T]): WeakTag[T] =
      new WeakTag[T] {
        override val tag: WeakTypeTag[T] = t
      }
  }

  trait WeakTagInstances0 extends WeakTagInstances1 {
    implicit def weakTagFromTag[T: Tag]: WeakTag[T] = WeakTag(Tag[T].tag)
  }

  trait WeakTagInstances1 {
    implicit def weakTagFromWeakTypeTag[T](implicit t: WeakTypeTag[T]): WeakTag[T] = WeakTag(t)
  }

}

