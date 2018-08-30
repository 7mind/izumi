package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.fixtures.HigherKindCases.HigherKindsCase1.OptionT
import com.github.pshirshov.izumi.distage.model.definition.With
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import distage.Tag
import org.scalatest.WordSpec

trait X[Y] {
  type Z = Y

  implicit def tagZ: Tag[Z]
}

class TagTest extends WordSpec with X[String] {

  def safe[T: TypeTag] = SafeType(typeOf[T])

  implicit class TagSafeType(tag: Tag[_]) {
    def tpe: SafeType = SafeType(tag.tag.tpe)
  }

  override final val tagZ = Tag[String]
  final val str = "str"

  trait T1[A, B, C, D, E, F[_]]
  trait Test[A, dafg, adfg, LS, L[_], SD, GG[A] <: L[A], ZZZ[_, _], S, SDD, TG]
  trait Y[V] extends X[V]
  case class ZOBA[A, B, C](value: Either[B, C])
  type Swap[A, B] = Either[B, A]
  type Id[A] = A
  type Id1[F[_], A] = F[A]

  "Tag" should {

    "Work for any concrete type" in {
      assert(Tag[Int].tpe == safe[Int])
      assert(Tag[Set[String]].tpe == safe[Set[String]])
      assert(Tag[Map[Boolean, Double]].tpe == safe[Map[Boolean, Double]])
      assert(Tag[_ => Unit].tpe == safe[_ => Unit])
      assert(Tag[Unit => _].tpe == safe[Unit => _])
      assert(Tag[_ => _].tpe == safe[_ => _])

      assert(Tag[Any].tpe == safe[Any])
      assert(Tag[Nothing].tpe == safe[Nothing])
      assert(Tag[Any => Nothing].tpe == safe[Any => Nothing])
      assert(Tag[Nothing => Any].tpe == safe[Nothing => Any])


      assert(Tag[With[Any]].tpe == safe[With[Any]])
      assert(Tag[With[Nothing]].tpe == safe[With[Nothing]])
      assert(Tag[With[_]].tpe == safe[With[_]])

      assert(Tag[{ def a: Int ; def g: Boolean }].tpe == safe[{ def a: Int ; def g: Boolean }])
      assert(Tag[Int with String].tpe == safe[Int with String])
      assert(Tag[Int { def a: Int }].tpe == safe[Int { def a: Int} ])

      assert(Tag[str.type].tpe == safe[str.type])
      assert(Tag[With[str.type] with ({ type T = str.type with Int})].tpe == safe[With[str.type] with ({ type T = str.type with Int})])
      assert(Tag[this.Z].tpe == safe[this.Z])
      assert(Tag[this.Z].tpe.tpe == typeOf[this.Z])
      assert(Tag[TagTest#Z].tpe == safe[TagTest#Z])
    }

    "Work for a concrete type with available TypeTag" in {
      val t_ = typeTag[Unit]

      {
        implicit val t: TypeTag[Unit] =  t_

        assert(Tag[Unit].tpe == safe[Unit])
      }
    }

    "Work for any abstract type with available TypeTag when obscured by empty refinement" in {
      def testTag[T: TypeTag] = Tag[T {}]

      assert(testTag[String].tpe == safe[String])
    }

    "Work for any abstract type with available Tag when obscured by empty refinement" in {
      def testTag[T: Tag] = Tag[T {}]

      assert(testTag[String].tpe == safe[String])
    }

    "handle function local type aliases" in {
      def testTag[T: Tag]= {
        type X[A] = Either[Int, A]

        Tag[X[T {}]]
      }

      assert(testTag[String].tpe == safe[Either[Int, String]])
    }

    "Work for an abstract type with available TagK when obscured by empty refinement" in {
      def testTagK[F[_]: TagK, T: Tag] = Tag[F[T {}] {}]
      // TODO add strange shapes

      assert(testTagK[Set, Int].tpe == safe[Set[Int]])
    }

    "Work for an abstract type with available TagK when TagK is requested through a type lambda" in {
      def testTagK[F[_], T: Tag](implicit ev: HKTag[{ type Arg[T1] = F[T1] }]) = Tag[F[T {}] {}]
      // TODO add strange shapes

      assert(testTagK[Set, Int].tpe == safe[Set[Int]])
    }

    "Work for an abstract type with available TagKK" in {
      def t1[F[_, _]: TagKK, T: Tag, G: Tag] = Tag[F[T, G]]

      assert(t1[ZOBA[Int, ?, ?], Int, String].tpe == safe[ZOBA[Int, Int, String]])
    }

    "Shouldn't work for any abstract type without available TypeTag or Tag or TagK" in {
      assertTypeError("""
      def testTag[T] = Tag[T]
      def testTagK[F[_], T] = Tag[F[T]]
         """)
    }

    "Work for any configuration of parameters" in {

      def t1[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F[_]: TagK]: Tag[T1[A, B, C, D, E, F]] = Tag[T1[A, B, C, D, E, F]]

      type ZOB[A, B, C] = Either[B, C]

      assert(t1[Int, Boolean, ZOB[Unit, Int, Int], TagK[Option], Nothing, ZOB[Unit, Int, ?]].tpe
        == safe[T1[Int, Boolean, Either[Int, Int], TagK[Option], Nothing, Either[Int, ?]]])

      def t2[A: Tag, dafg: Tag, adfg: Tag, LS: Tag, L[_]: TagK, SD: Tag, GG[A] <: L[A]: TagK, ZZZ[_, _]: TagKK, S: Tag, SDD: Tag, TG: Tag]: Tag[Test[A, dafg, adfg, LS, L, SD, GG, ZZZ, S, SDD, TG]] =
        Tag[Test[A, dafg, adfg, LS, L, SD, GG, ZZZ, S, SDD, TG]]

      assert(t2[TagTest.this.Z, TagTest.this.Z, TagTest.this.Z, TagTest.this.Z, X, TagTest.this.Z, Y, Either, TagTest.this.Z, TagTest.this.Z, TagTest.this.Z].tpe
        == safe[Test[String, String, String, String, X, String, Y, Either, String, String, String]])
    }

    "handle Swap type lambda" in {
      def t1[F[_, _]: TagKK, A: Tag, B: Tag] = Tag[F[A, B]]

      assert(t1[Swap, Int, String].tpe == safe[Either[String, Int]])
    }

    "handle Id type lambda" in {
      assert(TagK[Id].tag.tpe.toString.contains(".Id"))
    }

    "handle Id1 type lambda" in {

      assert(TagFK[Id1].tag.tpe.toString.contains(".Id1"))
    }

    // TODO: check errors on by commenting args in this
    "Assemble from higher than TagKK tags" in {
      def tag[T[_[_], _]: TagFK, F[_]: TagK, A: Tag] = Tag[T[F, A]]


      assert(tag[OptionT, Option, Int].tpe == safe[OptionT[Option, Int]])
    }

    "Handle abstract types instead of parameters" in {
      trait T1 {
        type F[G[_], A] = OptionT[G, A]
        type C[_, _]
        type G[_]
        type A
        type B

        def x: Tag[F[G, Either[A, B]]]
      }

      val t1 = new T1 {
        type G[T] = List[T]
        type C[A, B] = Either[A, B]
        type A = Int
        type B = Byte

        // Inconsistent handling of type aliases by scalac...
        // No TagK for G, but if G is inside an object or enclosing class
        // then there is a TagK
        val g: TagK[G] = TagK[List]

        final val x: Tag[F[G, Either[A, B]]] = {
          implicit val g0: TagK[G] = g
          Tag[F[G, C[A, B]]]
        }
      }

      assert(t1.x.tpe == safe[OptionT[List, Either[Int, Byte]]])
    }

    "progression test: can't handle parameters in structural types" in {
      assertTypeError("""
      def t[T: Tag]: Tag[{ type X = T }] = Tag[{ type X = T }]

      assert(t[Int].tpe == safe[{ type X = Int }])
      """)
    }

    "progression test: can't handle constrained generics i.e. <: Dep in TagK" in {
      assertTypeError("""
      import com.github.pshirshov.izumi.distage.fixtures.TypesCases.TypesCase3._

      TagK[Trait3]
      """)
    }
  }

}
