package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.ReflectionUtil.{Kind, kindOf}

import scala.annotation.implicitNotFound
import scala.language.higherKinds
import scala.reflect.api
import scala.reflect.api.{TypeCreator, Universe}

trait WithTags extends UniverseGeneric { self =>

  import u._
  import ReflectionUtil._

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
  @implicitNotFound("could not find implicit value for Tag[${T}]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in ${T}? i.e. def x[T: Tag, F[_]: TagK] = ...")
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

    implicit final def tagFromTypeTag[T](implicit t: TypeTag[T]): Tag[T] = Tag(t)

    /**
    * Create a Tag of a type formed by applying the type in `tag` to `args`.
    *
    * Example:
    * {{{
    * implicit def tagFromTagTAKA[T[_, _[_], _], K[_]: TagK, A0: Tag, A1: Tag](implicit t: TypeTag[T[Nothing, Nothing, Nothing]): Tag[T[A0, K, A1]] =
    *   Tag.appliedTag(t, List(Tag[A0].tag, TagK[K].tag, Tag[A1].tag))
    * }}}
    **/
    def appliedTag[R](tag: WeakTypeTag[_], args: List[TypeTag[_]]): Tag[R] = {
      val appliedTypeCreator = new TypeCreator {
        override def apply[U <: SingletonUniverse](m: api.Mirror[U]): U#Type = {
          m.universe.appliedType(tag.migrate(m).tpe.typeConstructor, args.map(_.migrate(m).tpe))
        }
      }
      Tag(TypeTag[R](tag.mirror, appliedTypeCreator))
    }

    /**
    * Create a Tag of a type formed from an `intersection` of types (A with B) with a structural refinement taken from `structType`
    *
    * `structType` is assumed to be a weak type of final result type, i.e.
    * {{{
    * Tag[A with B {def abc: Unit}] == refinedTag(List(typeTag[A], typeTag[B]), weakTypeTag[A with B { def abc: Unit }])
    * }}}
    **/
    def refinedTag[R](intersection: List[TypeTag[_]], structType: WeakTypeTag[_]): Tag[R] = {
      val refinedTypeCreator = new TypeCreator {
        override def apply[U <: SingletonUniverse](m: api.Mirror[U]): U#Type = {
          val parents = intersection.map(_.migrate(m).tpe)
          val struct = structType.migrate(m).tpe
          m.universe.internal.reificationSupport.setInfo(struct.typeSymbol, m.universe.internal.refinedType(parents, struct.decls))
          m.universe.internal.refinedType(parents, struct.decls, struct.typeSymbol)
        }
      }
      Tag(TypeTag[R](intersection.headOption.fold(rootMirror)(_.mirror), refinedTypeCreator))
    }

    def mergeArgs[R](tag: WeakTypeTag[_], args: List[Option[TypeTag[_]]]): Tag[R] = {
      val appliedTypeCreator = new TypeCreator {
        override def apply[U <: SingletonUniverse](m: api.Mirror[U]): U#Type = {
          val tpe = tag.migrate(m).tpe

          val mergedArgs = args.zipWithIndex.map {
            case (Some(t), _) => t.migrate(m).tpe
            case (None, i) => tpe.typeArgs.applyOrElse(i, (_: Int) =>
              throw new RuntimeException(s"Aaaa, can't get param $i of $tpe in constr ${tpe.typeConstructor} in args: ${args}"))
          }

          m.universe.appliedType(tpe.typeConstructor, mergedArgs)
        }
      }
      Tag(TypeTag[R](tag.mirror, appliedTypeCreator))
    }

  }

  trait LowPriorityTagInstances {
    implicit final def tagFromTagMaterializer[T](implicit t: TagMaterializer[self.type, T]): Tag[T] = t.value
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
    * As an argument to HKTag, you should supply a  specify the type variables your type will take and apply it to them.
    *
    * {{{
    *   type TagFGC[K[_[_, _], _[_], _[_[_], _, _, _]] = HKTag[ { type Arg[A[_, _], B[_], C[_[_], _, _, _]] = K[A, B, C] } ]
    * }}}
    */
  trait HKTag[T] {

    /**
     * Internal `TypeTag` holding the `typeConstructor` of type `T`
     **/
    def tag: TypeTag[_]

    override def toString: String = {
      val size = tag.tpe.typeParams.size
      // naming scheme
      size match {
        case 1 => s"TagK[${tag.tpe}]"
        case 2 => s"TagKK[${tag.tpe}]"
        case _ => s"Tag for ${tag.tpe} of kind ${kindOf(tag.tpe)}"
      }
    }
  }

  object HKTag {

    implicit def unsafeFromTypeTag[T](implicit k: TypeTag[T]): HKTag[T] = {
      new HKTag[T] {
        override def tag: TypeTag[_] = {
          val ctorCreator = new TypeCreator {
            override def apply[U <: SingletonUniverse](m: api.Mirror[U]): U#Type = {
              val t = k.migrate(m).tpe.decls.head.info
              t.typeConstructor
            }
          }

          TypeTag(k.mirror, ctorCreator)
        }
      }
    }
  }

  /**
    * `TagK` is a [[scala.reflect.api.TypeTags#TypeTag]] for higher-kinded types.
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
  type TagTK[K[_[_], _]] = HKTag[ { type Arg[A[_], B] = K[A, B] }]
  type TagTKK[K[_[_], _, _]] = HKTag[ { type  Arg[A[_], B, C] = K[A, B, C] }]
  type TagTK3[K[_[_], _, _, _]] = HKTag[ { type Arg[A[_], B, C, D] = K[A, B, C, D] } ]

  object TagK {
    /**
    * Construct a type tag for a higher-kinded type `K`
    *
    * Example:
    * {{{
    *     TagK[Option]
    * }}}
    **/
    def apply[K[_]: TagK]: TagK[K] = implicitly
  }

  object TagKK {
    def apply[K[_, _]: TagKK]: TagKK[K] = implicitly
  }

  object TagK3 {
    def apply[K[_, _, _]: TagK3]: TagK3[K] = implicitly
  }

  object TagT {
    def apply[K[_[_]]: TagT]: TagT[K] = implicitly
  }

  object TagTK {
    def apply[K[_[_], _]: TagTK]: TagTK[K] = implicitly
  }

  object TagTKK {
    def apply[K[_[_], _, _]: TagTKK]: TagTKK[K] = implicitly
  }

  object TagTK3 {
    def apply[K[_[_], _, _, _]: TagTK3]: TagTK3[K] = implicitly
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

  // workaround for a strange (prefix?) equality issue when splicing calls to `implicitly[RuntimeDIUniverse.u.TypeTag[X[Y]]`
  type ScalaReflectTypeTag[T] = u.TypeTag[T]
  type ScalaReflectWeakTypeTag[T] = u.WeakTypeTag[T]

}

object WithTags {
  final val defaultTagImplicitError: String =
    "could not find implicit value for Tag[${T}]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in ${T}? i.e. def x[T: Tag, F[_]: TagK] = ..."

  def hktagFormatMap: Map[Kind, String] = Map(
    Kind(Nil) -> s"Tag"
    , Kind(Kind(Nil) :: Nil) -> s"TagK"
    , Kind(Kind(Nil) :: Kind(Nil) :: Nil) -> s"TagKK"
    , Kind(Kind(Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> s"TagK3"
    , Kind(Kind(Kind(Nil) :: Nil) :: Nil) -> s"TagF"
    , Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Nil) -> s"TagFK"
    , Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> s"TagFKK"
    , Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> s"TagFK3"
  )

  def hktagFormat(tpe: Universe#Type): String = {
    val kind = kindOf(tpe)
    hktagFormatMap.get(kind) match {
      case Some(t) => s"$t[$tpe]"
      case _ => s"Tag for $tpe of kind $kind"
    }
  }
}
