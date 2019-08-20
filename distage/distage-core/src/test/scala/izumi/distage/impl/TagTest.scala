package izumi.distage.impl

import distage.{SafeType, Tag}
import izumi.distage.fixtures.HigherKindCases.HigherKindsCase1.OptionT
import izumi.distage.model.definition.With
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks._
import org.scalatest.WordSpec

trait X[Y] {
  type Z = Y

  implicit def tagZ: Tag[Z]
}

trait ZY {
  type T
  val x: String = "5"

  object y

}

class TagTest extends WordSpec with X[String] {

  def safe[T: TypeTag] = SafeType(typeOf[T])

  implicit class TagSafeType(tag: Tag[_]) {
    def toSafe: SafeType = SafeType(tag.tpe.tpe)
  }

  implicit class TagKSafeType(tagK: HKTag[_]) {
    def toSafe: SafeType = SafeType(tagK.tpe.tpe)
  }

  override final val tagZ = Tag[String]
  final val str = "str"

  trait T1[A, B, C, D, E, F[_]]

  trait T2[A, B, C[_[_], _], D[_], E]

  trait Test[A, dafg, adfg, LS, L[_], SD, GG[A] <: L[A], ZZZ[_, _], S, SDD, TG]

  trait Y[V] extends X[V]

  case class ZOBA[A, B, C](value: Either[B, C])

  type Swap[A, B] = Either[B, A]
  type Id[A] = A
  type Id1[F[_], A] = F[A]

  "Tag" should {

    "Work for any concrete type" in {
      assert(Tag[Int].toSafe == safe[Int])
      assert(Tag[Set[String]].toSafe == safe[Set[String]])
      assert(Tag[Map[Boolean, Double]].toSafe == safe[Map[Boolean, Double]])
      assert(Tag[_ => Unit].toSafe == safe[_ => Unit])
      assert(Tag[Unit => _].toSafe == safe[Unit => _])
      assert(Tag[_ => _].toSafe == safe[_ => _])

      assert(Tag[Any].toSafe == safe[Any])
      assert(Tag[Nothing].toSafe == safe[Nothing])
      assert(Tag[Any => Nothing].toSafe == safe[Any => Nothing])
      assert(Tag[Nothing => Any].toSafe == safe[Nothing => Any])
      assert(TagK[Identity].toSafe == TagK[Lambda[A => A]].toSafe)

      assert(Tag[With[Any]].toSafe == safe[With[Any]])
      assert(Tag[With[Nothing]].toSafe == safe[With[Nothing]])
      assert(Tag[With[_]].toSafe == safe[With[_]])

      assert(Tag[ {def a: Int; def g: Boolean}].toSafe == safe[ {def a: Int; def g: Boolean}])
      assert(Tag[Int with String].toSafe == safe[Int with String])
      assert(Tag[Int {def a: Int}].toSafe == safe[Int {def a: Int}])

      assert(Tag[str.type].toSafe == safe[str.type])
      assert(Tag[this.Z].toSafe == safe[this.Z])
      assert(Tag[TagTest#Z].toSafe == safe[TagTest#Z])

      assert(Tag[this.Z].toSafe.use(_ == typeOf[this.Z]))
    }

    "Work for structural concrete types" in {
      assert(Tag[With[str.type] with ({type T = str.type with Int})].toSafe == safe[With[str.type] with ({type T = str.type with Int})])
      assert(Tag[With[str.type] with ({type T = str.type with Int})].toSafe != safe[With[str.type] with ({type T = str.type with Long})])
    }

    "Work with odd type prefixes" in {
      val zy = new ZY {}

      assert(Tag[zy.T].toSafe == safe[zy.T])
      assert(Tag[zy.x.type].toSafe == safe[zy.x.type])
      assert(Tag[zy.y.type].toSafe == safe[zy.y.type])
    }

    "Work with subtyping odd type prefixes" in {
      val zy = new ZY {}

      val b1 = Tag[zy.T].toSafe <:< safe[zy.T]
      val b2 = Tag[zy.x.type].toSafe <:< safe[zy.x.type]
      val b3 = Tag[zy.y.type].toSafe <:< safe[zy.y.type]

      assert(b1)
      assert(b2)
      assert(b3)
    }

    "Use TypeTag instance when available" in {
      val t_ = typeTag[Unit]

      {
        implicit val t: TypeTag[Unit] = t_

        assert(Tag[Unit].tpe eq t)
      }
    }

    "Work for any abstract type with available TypeTag when obscured by empty refinement" in {
      def testTag[T: TypeTag] = Tag[T {}]

      assert(testTag[String].toSafe == safe[String])
    }

    "Work for any abstract type with available Tag when obscured by empty refinement" in {
      def testTag[T: Tag] = Tag[T {}]

      assert(testTag[String].toSafe == safe[String])
    }

    "Work for any abstract type with available Tag while preserving additional refinement" in {
      def testTag[T: Tag] = Tag[T {def x: Int}]

      assert(testTag[String].toSafe == safe[String {def x: Int}])
    }

    "handle function local type aliases" in {
      def testTag[T: Tag] = {
        type X[A] = Either[Int, A]

        Tag[X[T {}]]
      }

      assert(testTag[String].toSafe == safe[Either[Int, String]])
    }

    "Work for an abstract type with available TagK when obscured by empty refinement" in {
      def testTagK[F[_] : TagK, T: Tag] = Tag[F[T {}] {}]

      assert(testTagK[Set, Int].toSafe == safe[Set[Int]])
    }

    "Work for an abstract type with available TagK when TagK is requested through an explicit implicit" in {
      def testTagK[F[_], T: Tag](implicit ev: HKTag[ {type Arg[C] = F[C]}]) = {
        ev.discard()
        Tag[F[T {}] {}]
      }

      assert(testTagK[Set, Int].toSafe == safe[Set[Int]])
    }

    "xxx" in {

    }

    "Tag.auto.T kind inference macro works for known cases" in {
      def x[T[_] : Tag.auto.T]: TagK[T] = implicitly[Tag.auto.T[T]]

      def x2[T[_, _] : Tag.auto.T]: TagKK[T] = implicitly[Tag.auto.T[T]]

      def x3[T[_, _, _[_[_], _], _[_], _]](implicit x: Tag.auto.T[T]): Tag.auto.T[T] = x

      val b1 = x[Option].tpe.tpe =:= TagK[Option].tpe.tpe
      val b2 = x2[Either].tpe.tpe =:= TagKK[Either].tpe.tpe
      val b3 = implicitly[Tag.auto.T[OptionT]].tag =:= TagTK[OptionT].tag
      val b4 = x3[T2].tpe.tpe.typeConstructor =:= safe[T2[Nothing, Nothing, Nothing, Nothing, Nothing]].tpe.typeConstructor

      assert(b1)
      assert(b2)
      assert(b3)
      assert(b4)
    }

    "Work for an abstract type with available TagKK" in {
      def t1[F[_, _] : TagKK, T: Tag, G: Tag] = Tag[F[T, G]]

      assert(t1[ZOBA[Int, ?, ?], Int, String].toSafe == safe[ZOBA[Int, Int, String]])
    }

    "Handle Tags outside of a predefined set" in {
      type TagX[T[_, _, _[_[_], _], _[_], _]] = HKTag[ {type Arg[A, B, C[_[_], _], D[_], E] = T[A, B, C, D, E]}]

      def testTagX[F[_, _, _[_[_], _], _[_], _] : TagX, A: Tag, B: Tag, C[_[_], _] : TagTK, D[_] : TagK, E: Tag] = Tag[F[A, B, C, D, E]]

      val value = testTagX[T2, Int, String, OptionT, List, Boolean]
      assert(value.toSafe == safe[T2[Int, String, OptionT, List, Boolean]])
    }

    "Shouldn't work for any abstract type without available TypeTag or Tag or TagK" in {
      assertTypeError(
        """
      def testTag[T] = Tag[T]
      def testTagK[F[_], T] = Tag[F[T]]
         """)
    }

    "Work for any configuration of parameters" in {

      def t1[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F[_] : TagK]: Tag[T1[A, B, C, D, E, F]] = Tag[T1[A, B, C, D, E, F]]

      type ZOB[A, B, C] = Either[B, C]

      assert(t1[Int, Boolean, ZOB[Unit, Int, Int], TagK[Option], Nothing, ZOB[Unit, Int, ?]].toSafe
        == safe[T1[Int, Boolean, Either[Int, Int], TagK[Option], Nothing, Either[Int, ?]]])

      def t2[A: Tag, dafg: Tag, adfg: Tag, LS: Tag, L[_] : TagK, SD: Tag, GG[A] <: L[A] : TagK, ZZZ[_, _] : TagKK, S: Tag, SDD: Tag, TG: Tag]: Tag[Test[A, dafg, adfg, LS, L, SD, GG, ZZZ, S, SDD, TG]] =
        Tag[Test[A, dafg, adfg, LS, L, SD, GG, ZZZ, S, SDD, TG]]

      assert(t2[TagTest.this.Z, TagTest.this.Z, T1[ZOB[String, Int, Byte], String, String, String, String, List], TagTest.this.Z, X, TagTest.this.Z, Y, Either, TagTest.this.Z, TagTest.this.Z, TagTest.this.Z].toSafe
        == safe[Test[String, String, T1[Either[Int, Byte], String, String, String, String, List], String, X, String, Y, Either, String, String, String]])
    }

    "handle Swap type lambda" in {
      def t1[F[_, _] : TagKK, A: Tag, B: Tag] = Tag[F[A, B]]

      assert(t1[Swap, Int, String].toSafe == safe[Either[String, Int]])
    }

    "handle Id type lambda" in {
      assert(TagK[Id].tpe.tpe.toString.contains(".Id"))
    }

    "handle Id1 type lambda" in {

      assert(TagTK[Id1].tpe.tpe.toString.contains(".Id1"))
    }

    "Assemble from higher than TagKK tags" in {
      def tag[T[_[_], _] : TagTK, F[_] : TagK, A: Tag] = Tag[T[F, A]]


      assert(tag[OptionT, Option, Int].toSafe == safe[OptionT[Option, Int]])
    }

    "Handle abstract types instead of parameters" in {
      trait T1 {
        type F[F0[_], A0] = OptionT[F0, A0]
        type C[_, _]
        type G[_]
        type A
        type B

        def x: Tag[F[G, Either[A, B]]]
      }

      val t1: T1 {
        type G[T] = List[T]

        type C[A0, B0] = Either[A0, B0]

        type A = Int

        type B = Byte
      } = new T1 {
        type G[T] = List[T]
        type C[A0, B0] = Either[A0, B0]
        type A = Int
        type B = Byte

        // Inconsistent handling of type aliases by scalac...
        // No TagK for G, but if G is inside an object or enclosing class
        // then there is a TagK
        val g: TagK[G] = TagK[List]

        final val x: Tag[F[G, Either[A, B]]] = {
          implicit val g0: TagK[G] = g
          g0.discard()
          Tag[F[G, C[A, B]]]
        }
      }

      assert(t1.x.toSafe == safe[OptionT[List, Either[Int, Byte]]])
    }

    "Can create custom type tags to support bounded generics, e.g. <: Dep in TagK" in {
      import izumi.distage.fixtures.TypesCases.TypesCase3._

      type `TagK<:Dep`[K[_ <: Dep]] = HKTag[ {type Arg[A <: Dep] = K[A]}]

      implicitly[`TagK<:Dep`[Trait3]].tpe.tpe =:= typeOf[Trait3[Nothing]].typeConstructor
    }

    "scalac bug: can't find HKTag when obscured by type lambda" in {
      assertCompiles("HKTag.hktagFromTypeTag[{ type Arg[C] = Option[C] }]")
      assertTypeError("HKTag.hktagFromTypeTag[({ type l[F[_]] = HKTag[{ type Arg[C] = F[C] }] })#l[Option]]")
      // The error produced above is:
      //   Error:(177, 32) No TypeTag available for izumi.distage.model.reflection.universe.RuntimeDIUniverse.HKTag[Object{type Arg[C] = Option[C]}]
      //   HKTag.unsafeFromTypeTag[({ type l[F[_]] = HKTag[{ type Arg[C] = F[C] }] })#l[Option]]
      // That means that result of applying lambda:
      //  `Lambda[(F[_]) => HKTag[{ type Arg[C] = F[C] }] }][Option]`
      //  != HKTag[{ type Arg[C] = F[C] }]
      // For Scalac. which necessitates another macro call for fixup ._.
    }

    "progression test: type tags with bounds are not currently requested by the macro" in {
      assertTypeError(
        """
      import izumi.distage.fixtures.TypesCases.TypesCase3._

      type `TagK<:Dep`[K[_ <: Dep]] = HKTag[ { type Arg[A <: Dep] = K[A] } ]

      def t[T[_ <: Dep]: `TagK<:Dep`, A: Tag] = Tag[T[A]]

      assert(t[Trait3, Dep].tpe == safe[Trait3[Dep]])
      """)
      // def t1[U, T[_ <: U], A <: U: Tag](implicit ev: TagKUBound[U, T]) = Tag[T[A]]
      // assert(t1[Dep, Trait3, Dep].tpe == safe[Trait3[Dep]])
    }

    "progression test: can't handle parameters in structural types yet" in {
      assertTypeError(
        """
      def t[T: Tag]: Tag[{ type X = T }] = Tag[{ type X = T }]

      assert(t[Int].tpe == safe[{ type X = Int }])
      """)
    }

    "progression test: Can't materialize TagK for type lambdas that close on a generic parameter with available Tag" in {
      assertTypeError(
        """
        def partialEitherTagK[A: Tag] = TagK[Either[A, ?]]

        print(partialEitherTagK[Int])
        assert(partialEitherTagK[Int] != null)
      """)
    }

  }

}
