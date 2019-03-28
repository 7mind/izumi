package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.ReflectionUtil.{Kind, kindOf}
import com.github.pshirshov.izumi.fundamentals.reflection.WithTags.hktagFormat

import scala.annotation.implicitNotFound
import scala.language.experimental.macros
import scala.reflect.api
import scala.reflect.api.{TypeCreator, Universe}

trait WithTags extends UniverseGeneric { self =>

  import u._
  import ReflectionUtil._

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
  *   * Type Parameters do not yet resolve inside higher-kinded type lambdas, e.g. T in {{{ TagK[Either[T, ?]] }}}
  *   * TagK* does not resolve for constructors with bounded parameters, e.g. S in {{{ class Abc[S <: String]; TagK[Abc] }}}
  *     (You can still have a bound in partial application: e.g. {{{ class Abc[S <: String, A]; TagK[Abc["hi", ?]] }}}
  *   * Further details at [[https://github.com/pshirshov/izumi-r2/pull/369]]
  */
  @implicitNotFound("could not find implicit value for Tag[${T}]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in ${T}? e.g. def x[T: Tag, F[_]: TagK] = ...")
  trait Tag[T] {
    def tag: TypeTag[T]

    override def toString: String = s"Tag[${tag.tpe}]"
  }

  object Tag extends LowPriorityTagInstances {

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
      * */
    def auto: Any = macro TagLambdaMacro.lambdaImpl

    def apply[T: Tag]: Tag[T] = implicitly

    def apply[T](t: TypeTag[T]): Tag[T] =
      new Tag[T] {
        override val tag: TypeTag[T] = t
      }

    /** Resulting [Tag] will not have the ability to migrate into a different universe
      * (which is not usually a problem, but still worth naming it 'unsafe')
      */
    def unsafeFromType[T](currentMirror: u.Mirror)(tpe: Type): Tag[T] =
      new Tag[T] {
        override val tag: TypeTag[T] = ReflectionUtil.typeToTypeTag[T](u: u.type)(tpe, currentMirror)
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
      * `structType` is assumed to be a weak type of final result type, e.g.
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
      Tag(TypeTag[R](intersection.headOption.fold(u.rootMirror)(_.mirror), refinedTypeCreator))
    }

    /** For construction from [[TagLambdaMacro]] */
    type HKTagRef[T] = HKTag[T]

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
    * As an argument to HKTag, you should specify the type variables your type parameter will take and apply them to it, in order.
    *
    * {{{
    *   type TagFGC[K[_[_, _], _[_], _[_[_], _, _, _]] = HKTag[ { type Arg[A[_, _], B[_], C[_[_], _, _, _]] = K[A, B, C] } ]
    * }}}
    *
    * A convenience macro `Tag.auto.T` is available to automatically create a type lambda from a type of any kind:
    *
    * {{{
    *   def x[K[_[_, _], _[_], _[_[_], _, _, _]: Tag.auto.T]: Tag.auto.T[K] = implicitly[Tag.auto.T[K]]
    * }}}
    *
    */
  trait HKTag[T] {

    /**
     * Internal `TypeTag` holding the `typeConstructor` of type `T`
     **/
    def tag: TypeTag[_]

    override def toString: String =
      s"HKTag(${hktagFormat(tag.tpe)})"
  }

  object HKTag extends LowPriorityHKTagInstances {
    // TODO: add macro with compile-time sanity check
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

  trait LowPriorityHKTagInstances {
    implicit def hktagFromHKTagMaterializer[T](implicit t: HKTagMaterializer[self.type, T]): HKTag[T] = t.value
  }

  /**
    * `TagK` is a [[scala.reflect.api.TypeTags.TypeTag]] for higher-kinded types.
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

// TODO
//  type TagKUBound[U, K[_ <: U]] = HKTag[{ type Arg[A <: U] = K[A] }]
//
//  object TagKUBound {
//    def apply[U, K[_ <: U]](implicit ev: TagKUBound[U, K]): TagKUBound[U, K] = implicitly
//  }

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

  // workaround for being unable to refer to Tag object's type from a type projection (?)
  type TagObject = Tag.type
}

object WithTags {
  final val defaultTagImplicitError: String =
    "could not find implicit value for Tag[${T}]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in ${T}? e.g. def x[T: Tag, F[_]: TagK] = ..."

  def hktagFormatMap: Map[Kind, String] = Map(
    Kind(Nil) -> "Tag"
    , Kind(Kind(Nil) :: Nil) -> "TagK"
    , Kind(Kind(Nil) :: Kind(Nil) :: Nil) -> "TagKK"
    , Kind(Kind(Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagK3"
    , Kind(Kind(Kind(Nil) :: Nil) :: Nil) -> "TagT"
    , Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Nil) -> "TagTK"
    , Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagTKK"
    , Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagTK3"
  )

  def hktagFormat(tpe: Universe#Type): String = {
    val kind = kindOf(tpe)
    hktagFormatMap.get(kind) match {
      case Some(t) => s"$t[$tpe]"
      case _ => s"Tag for $tpe of kind $kind"
    }
  }
}
