package com.github.pshirshov.izumi.distage.model.reflection.universe

import scala.language.higherKinds
import scala.reflect.api
import scala.reflect.api.{TypeCreator, Universe}

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
  *
  *     class MyModule[F[_]: Monad: TagK] {
  *       make[MyService[F]]
  *       make[F[Unit]].named("empty").from(Monad[F].pure())
  *     }
  *
  * Without a `TagK` constraint above, this example would fail with `no TypeTag available for MyService[F]`
  */
  trait Tag[T] {
    def tag: TypeTag[T]

    override def toString: String = s"Tag[${tag.tpe}]"
  }

  object Tag extends TagInstances0 {
    def apply[T: Tag]: Tag[T] = implicitly[Tag[T]]

    def apply[T](t: TypeTag[T]): Tag[T] =
      new Tag[T] {
        override val tag: TypeTag[T] = t
      }

    /**
    * Helper for creating your own instances
    **/
    def appliedTag[R](tag: WeakTypeTag[_], args: List[TypeTag[_]]): Tag[R] = {
      val appliedTypeCreator = new TypeCreator {
        override def apply[U <: Universe with Singleton](m: api.Mirror[U]): U#Type =
          m.universe.appliedType(tag.migrate(m).tpe.typeConstructor, args.map(_.migrate(m).tpe))
      }
      Tag(TypeTag[R](tag.mirror, appliedTypeCreator))
    }

    // hmm, it works, but not always...
    implicit final val tagNothing: Tag[Nothing] =
      new Tag[Nothing] {
        override val tag: TypeTag[Nothing] = typeTag[Nothing]
      }
  }

  trait TagInstances0 extends TagInstances1 {
    // `Any` is apparently kind polymorphic (only inside the compiler, you can't use that ._.), which breaks other instances, so it's handled separately.
    implicit val tagAny: Tag[Any] =
      new Tag[Any] {
        override val tag: TypeTag[Any] = typeTag[Any]
      }

//
//    implicit final val tagNothing: Tag[Nothing] =
//      new Tag[Nothing] {
//        override val tag: TypeTag[Nothing] = typeTag[Nothing]
//      }
//
//    implicit def tagDNothing[A <: Nothing]: Tag[A] = ???
//
//    implicit def tagFromTypeTagA[A: TypeTag]: Tag[A] = Tag(typeTag[A])

    implicit def tagUNothing[A >: Nothing](implicit ev: A =:= Nothing): Tag[A] = ???

  }

  trait TagInstances1 extends TagInstances2 {

    implicit def tagFromTypeTagA[A: TypeTag]: Tag[A] = Tag(typeTag[A])

    implicit def tagFromTypeTagKA[K[_], A](implicit t: TypeTag[K[A]]): Tag[K[A]] = Tag(t)

    implicit def tagFromTypeTagKAA[K[_, _], A0, A1](implicit t: TypeTag[K[A0, A1]]): Tag[K[A0, A1]] = Tag(t)

    implicit def tagFromTypeTagTK[T[_[_]], K[_]](implicit t: TypeTag[T[K]]): Tag[T[K]] = Tag(t)

    implicit def tagFromTypeTagTKA[T[_[_], _], K[_], A](implicit t: TypeTag[T[K, A]]): Tag[T[K, A]] = Tag(t)

    implicit def tagFromTypeTagTKAA[T[_[_], _, _], K[_], A0, A1](implicit t: TypeTag[T[K, A0, A1]]): Tag[T[K, A0, A1]] = Tag(t)

    implicit def tagFromTypeTagTKAAA[T[_[_], _, _, _], K[_], A0, A1, A2](implicit t: TypeTag[T[K, A0, A1, A2]]): Tag[T[K, A0, A1, A2]] = Tag(t)

    implicit def tagFromTypeTagTKAAAA[T[_[_], _, _, _, _], K[_], A0, A1, A2, A3](implicit t: TypeTag[T[K, A0, A1, A2, A3]]): Tag[T[K, A0, A1, A2, A3]] = Tag(t)

    implicit def tagFromTypeTagTKAAAAA[T[_[_], _, _, _, _, _], K[_], A0, A1, A2, A3, A4](implicit t: TypeTag[T[K, A0, A1, A2, A3, A4]]): Tag[T[K, A0, A1, A2, A3, A4]] = Tag(t)

  }

  trait TagInstances2  {

    implicit def tagFromTagKA[K[_]: TagK, A: Tag]: Tag[K[A]] =
      TagK[K].apply[A]

    implicit def tagFromTagKAA[K[_, _], A0: Tag, A1: Tag](implicit t: WeakTypeTag[K[A0, A1]]): Tag[K[A0, A1]] =
      Tag.appliedTag(t, List(Tag[A0].tag, Tag[A1].tag))

    implicit def tagFromTagTK[T[_[_]], K[_]: TagK](implicit t: WeakTypeTag[T[K]]): Tag[T[K]] =
      Tag.appliedTag(t, List(TagK[K].ctorTag))

    implicit def tagFromTagTKA[T[_[_], _], K[_]: TagK, A: Tag](implicit t: WeakTypeTag[T[K, A]]): Tag[T[K, A]] =
      Tag.appliedTag(t, List(TagK[K].ctorTag, Tag[A].tag))

    implicit def tagFromTagTKAA[T[_[_], _, _], K[_]: TagK, A0: Tag, A1: Tag](implicit t: WeakTypeTag[T[K, A0, A1]]): Tag[T[K, A0, A1]] =
      Tag.appliedTag(t, List(TagK[K].ctorTag, Tag[A0].tag, Tag[A1].tag))

    implicit def tagFromTagTKAAA[T[_[_], _, _, _], K[_]: TagK, A0: Tag, A1: Tag, A2: Tag](implicit t: WeakTypeTag[T[K, A0, A1, A2]]): Tag[T[K, A0, A1, A2]] =
      Tag.appliedTag(t, List(TagK[K].ctorTag, Tag[A0].tag, Tag[A1].tag, Tag[A2].tag))

    implicit def tagFromTagTKAAAA[T[_[_], _, _, _, _], K[_]: TagK, A0: Tag, A1: Tag, A2: Tag, A3: Tag](implicit t: WeakTypeTag[T[K, A0, A1, A2, A3]]): Tag[T[K, A0, A1, A2, A3]] =
      Tag.appliedTag(t, List(TagK[K].ctorTag, Tag[A0].tag, Tag[A1].tag, Tag[A2].tag, Tag[A3].tag))

    implicit def tagFromTagTKAAAAA[T[_[_], _, _, _, _, _], K[_]: TagK, A0: Tag, A1: Tag, A2: Tag, A3: Tag, A4: Tag](implicit t: WeakTypeTag[T[K, A0, A1, A2, A3, A4]]): Tag[T[K, A0, A1, A2, A3, A4]] =
      Tag.appliedTag(t, List(TagK[K].ctorTag, Tag[A0].tag, Tag[A1].tag, Tag[A2].tag, Tag[A3].tag, Tag[A4].tag))
  }

  /**
  * `TagK` is a [[scala.reflect.api.TypeTags#TypeTag]] for higher-kinded types.
  *
  * Example:
  *     def containersEqual[F[_]: TagK, K[_]: TagK](containerF: F[_], containerK: K[_]): Boolean {
  *
  *     }
  */
  trait TagK[K[_]] {

    /**
    * Internal `TypeTag` holding the `typeConstructor` of type `K`
    *
    * You probably want to use `apply` method to replace a `TypeTag` of T[F] with T[K] instead.
    **/
    def ctorTag: TypeTag[_]

    /**
    * Create a [[scala.reflect.api.TypyeTags#TypeTag]] for `K[T]` by applying `K[_]` to `T`
    *
    * Use:
    *     TagK[List].apply[Int]
    *
    *     > typeTag[List[Int]]
    */
    final def apply[T](implicit tag: Tag[T]): Tag[K[T]] =
      Tag.appliedTag(ctorTag, List(tag.tag))

    override final def toString: String = s"TagK[${ctorTag.tpe}]"
  }

  object TagK extends TagKInstances {
    /**
    * Construct a type tag for a higher-kinded type `K`
    *
    * Use:
    *     TagK[Option]
    **/
    def apply[K[_] : TagK]: TagK[K] = implicitly[TagK[K]]
  }

  trait TagKInstances {
    implicit def tagKFromTypeTag[K[_]](implicit t: TypeTag[K[Nothing]]): TagK[K] =
      new TagK[K] {
        override val ctorTag: TypeTag[_] = {
          val ctorKCreator = new TypeCreator {
            override def apply[U <: Universe with Singleton](m: api.Mirror[U]): U#Type =
              t.migrate(m).tpe.typeConstructor
          }
          TypeTag(t.mirror, ctorKCreator)
        }
      }
  }

  implicit class WeakTypeTagExtensions[T](val weakTypeTag: WeakTypeTag[T]) {
    def migrate[U <: Universe with Singleton](m: api.Mirror[U]): m.universe.WeakTypeTag[T] =
      weakTypeTag.in(m).asInstanceOf[m.universe.WeakTypeTag[T]]
  }

}

