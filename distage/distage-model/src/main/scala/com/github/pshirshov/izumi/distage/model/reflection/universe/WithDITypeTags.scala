package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.reflection.macros.TagMacroImpl
import com.github.pshirshov.izumi.fundamentals.reflection.SingletonUniverse

import scala.language.experimental.macros
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

  object Tag extends LowPriorityTagInstances {
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
            case (None, i) => tpe.typeArgs.applyOrElse(i, (_: Int) => throw new RuntimeException(s"Aaaa, can't get param $i of $tpe in constr ${tpe.typeConstructor} in args: ${args}"))
          }

          m.universe.appliedType(tpe.typeConstructor, mergedArgs)
        }
      }
      Tag(TypeTag[R](tag.mirror, appliedTypeCreator))
    }

    implicit def tagFromTypeTag[T](implicit t: TypeTag[T]): Tag[T] = Tag(t)
  }

  trait LowPriorityTagInstances {
    implicit def tagFromMacro[T]: Tag[T] = macro TagMacroImpl.impl[T]
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

