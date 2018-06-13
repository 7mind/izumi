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
  }

  trait TagInstances0 extends TagInstances1 {

    implicit def tagFromTypeTagK[T[_], A](implicit t: TypeTag[T[A]]): Tag[T[A]] =
      new Tag[T[A]] {
        override implicit val tag: u.TypeTag[T[A]] = t
      }

  }

  trait TagInstances1 extends TagInstances2 {

    implicit def tagFromTypeTag[T: TypeTag]: Tag[T] =
      new Tag[T] {
        override val tag: u.TypeTag[T] = typeTag[T]
      }

    implicit def tagFromTypeTagKK[T[_[_]], K[_]](implicit t: TypeTag[T[K]]): Tag[T[K]] =
      new Tag[T[K]] {
        override implicit val tag: u.TypeTag[T[K]] = t
      }
  }

  trait TagInstances2  {

    implicit def tagFromTagKTK[T[_[_]], K[_] : TagK](implicit t: WeakTypeTag[T[K]]): Tag[T[K]] =
      new Tag[T[K]] {
        override val tag: u.TypeTag[T[K]] = TagK[K].replaceIn(t)
      }

    implicit def tagFromTagKKT[K[_]: TagK, A](implicit t: Tag[A]): Tag[K[A]] =
      new Tag[K[A]] {
        override val tag: u.TypeTag[K[A]] = TagK[K].apply(t.tag)
      }
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
    * Create a [[scala.reflect.api.TypyeTags#TypeTag]] for `K[T]` by applying `K[_]` to `T`
    *
    * Use:
    *     TagK[List].apply[Int]
    *
    *     > typeTag[List[Int]]
    */
    def apply[T](implicit tag: TypeTag[T]): TypeTag[K[T]] = {
      val appliedKTCreator = new TypeCreator {
        override def apply[U <: Universe with Singleton](m: api.Mirror[U]): U#Type =
          m.universe.appliedType(
            ctorTag.in(m).tpe.asInstanceOf[m.universe.Type], tag.in(m).tpe.asInstanceOf[m.universe.Type]
          )
      }
      TypeTag(tag.mirror, appliedKTCreator)
    }

    /**
    * Replace a [[scala.reflect.api.TypeTags#TypeTag]] for T[F] wit a `TypeTag` for T[K]
    *
    * This is the main mechanism by which `TagK` works.
    * First we retrieve a TypeTag for T[Nothing], then we replace the `Nothing` with `K`
    *
    * Use:
    *     TagK[List].replaceIn(weakTypeTag[OptionT[Try]])
    *
    *     > typeTag[OptionT[List]]
    **/
    def replaceIn[T[_[_]], F[_]](tag: WeakTypeTag[T[F]]): TypeTag[T[K]] = {
      val appliedTKCreator = new TypeCreator {
        override def apply[U <: Universe with Singleton](m: api.Mirror[U]): U#Type = {
          val tCtor = tag.in(m).tpe.typeConstructor
          m.universe.appliedType(
            tCtor.asInstanceOf[m.universe.Type], List(ctorTag.in(m).tpe.asInstanceOf[m.universe.Type])
          )
        }
      }
      TypeTag(tag.mirror, appliedTKCreator)
    }

    /**
    * Internal `TypeTag` holding the `typeConstructor` of type `K`
    *
    * You probably want to use `apply` method to replace a `TypeTag` of T[F] with T[K] instead.
    **/
    def ctorTag: TypeTag[_]

    override def toString: String = s"TagK[${ctorTag.tpe}]"
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
    implicit def tagKFromTypeTag[K[_]](implicit ev: TypeTag[K[Nothing]]): TagK[K] =
      new TagK[K] {
        override val ctorTag: TypeTag[_] = {
          val ctorKCreator = new TypeCreator {
            override def apply[U <: Universe with Singleton](m: api.Mirror[U]): U#Type =
              ev.in(m).tpe.typeConstructor
          }
          TypeTag.apply(ev.mirror, ctorKCreator)
        }
      }
  }

}

