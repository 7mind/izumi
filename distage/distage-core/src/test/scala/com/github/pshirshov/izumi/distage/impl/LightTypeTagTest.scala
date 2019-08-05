package com.github.pshirshov.izumi.distage.impl

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti._
import org.scalatest.WordSpec

class LightTypeTagTest extends WordSpec {

  trait T0[A[_], B[_]]

  type F[T] = T
  type FP1[+T] = List[T]
  type FP[+T] = FP1[T]
  type L[P] = List[P]
  type LN[P <: Number] = List[P]

  trait T1[U[_]]

  type FI[IGNORE] = Unit

  trait T2[U[_[_], _[_]]]

  //  type K = T1[F]
  //  val a: K = new T1[F] {}
  trait C {
    type A
  }

  trait R[K, A <: R[K, A]]

  trait R1[K] extends R[K, R1[K]]

  type S[A, B] = Either[B, A]

  trait W1

  trait W2 extends W1

  trait W3[_]

  trait W4[A] extends W3[A]

  trait I1

  trait I2 extends I1

  trait F1[+A]

  trait F2[+A] extends F1[A]

  trait FT1[+A[+ _[+ _]]]

  trait FT2[+A[+ _[+ _]]] extends FT1[A]

  trait IT1[+K[+ _]]

  trait IT2[+K[+ _]] extends IT1[K]

  trait FM1[+A, +B]

  trait FM2[+A] extends FM1[A, Unit]

  type NestedTL[G[_, _], A, B] = FM2[G[A, (B, A)]]

  type NestedTL2[A, B, G[_]] = FM2[G[S[B, A]]]

  type Const[A, B] = B

  trait H1

  trait H2 extends H1

  trait H3 extends H2

  trait H4 extends H3

  trait H5 extends H4

  def println(o: Any) = info(o.toString)

  def println(o: FLTT) = info(o.t.toString)

  def assertRepr(t: FLTT, expected: String): Unit = {
    assert(t.toString == expected)
    ()
  }

  def assertSame(t: FLTT, expected: FLTT): Unit = {
    info(s"$t =?= $expected")
    assert(t =:= expected)
    ()
  }

  def assertDifferent(t: FLTT, expected: FLTT): Unit = {
    info(s"$t =!= $expected")
    assert(!(t =:= expected))
    ()
  }

  def assertChild(child: FLTT, parent: FLTT): Unit = {
    info(s"$child <?< $parent")
    assert(child <:< parent)
    ()
  }

  def assertNotChild(child: FLTT, parent: FLTT): Unit = {
    info(s"$child <!< $parent")
    assert(!(child <:< parent))
    ()
  }


  def assertCombine(outer: FLTT, inner: Seq[FLTT], expected: FLTT): Unit = {
    val combined = outer.combine(inner: _*)
    info(s"($outer)•(${inner.mkString(",")}) => $combined =?= $expected")
    assert(combined == expected)
    ()
  }

  def assertCombine(outer: FLTT, inner: FLTT, expected: FLTT): Unit = {
    val combined = outer.combine(inner)
    info(s"($outer)•($inner) => $combined =?= $expected")
    assert(combined == expected)
    ()
  }

  def assertCombineNonPos(outer: FLTT, inner: Seq[Option[FLTT]], expected: FLTT): Unit = {
    val combined = outer.combineNonPos(inner: _*)
    info(s"($outer)•(${inner.mkString(",")}) => $combined =?= $expected")
    assert(combined == expected)
    ()
  }

  "lightweight type tags" should {
    "support human-readable representation" in {
      println(LTT[Int {def a(k: String): Int; val b: String; type M1 = W1; type M2 <: W2; type M3[A] = Either[Unit, A]}])
      assertRepr(LTT[I1 with (I1 with (I1 with W1))], "{LightTypeTagTest::I1 & LightTypeTagTest::W1}")
      assertRepr(`LTT[_]`[R1], "λ %0 → LightTypeTagTest::R1[=0]")
      assertRepr(`LTT[_]`[Nothing], "Nothing")
      assertRepr(LTT[Int], "Int")
      assertRepr(LTT[List[Int]], "List[+Int]")
      assertRepr(LTT[F[Int]], "Int")
      assertRepr(LTT[FP[Int]], "List[+Int]")
      assertRepr(`LTT[_]`[L], "λ %0 → List[+0]")
      assertRepr(`LTT[_]`[Either[Unit, ?]], "λ %0 → Either[+Unit,+0]")
      assertRepr(`LTT[_]`[S[Unit, ?]], "λ %0 → Either[+0,+Unit]")
    }

    "support typetag combination" in {
      assertCombine(`LTT[_[_]]`[T1], `LTT[_]`[F], LTT[T1[F]])
      assertCombine(`LTT[_[_]]`[T1], `LTT[_]`[FP], LTT[T1[FP]])
      assertCombine(`LTT[_[_]]`[T1], `LTT[_]`[FI], LTT[T1[FI]])

      assertCombine(`LTT[_[_]]`[T0[F, ?[_]]], `LTT[_]`[FP], LTT[T0[F, FP]])
      assertCombine(`LTT[_[_]]`[T1], `LTT[_]`[List], LTT[T1[List]])
      assertCombine(`LTT[_]`[List], LTT[Int], LTT[List[Int]])
      assertCombine(`LTT[_,_]`[Either], LTT[Unit], `LTT[_]`[Either[Unit, ?]])

      assertCombine(`LTT[_[_[_],_[_]]]`[T2], `LTT[_[_],_[_]]`[T0], LTT[T2[T0]])

      type ComplexRef[T] = W1 with T {def a(p: T): T; type M = T}
      assertCombine(`LTT[_]`[ComplexRef], LTT[Int], LTT[W1 with Int {def a(p: Int): Int; type M = Int}])
    }

    "support non-positional typetag combination" in {
      assertCombineNonPos(`LTT[_,_]`[Either], Seq(None, Some(LTT[Unit])), `LTT[_]`[Either[?, Unit]])
    }

    "xxx" in {
//      assertChild(LTT[FT2[IT2]], LTT[FT1[IT1]])
//      assertChild(`LTT[_[_[_]]]`[FT2].combine(`LTT[_[_]]`[IT2]), LTT[FT1[IT1]])

//      trait KT1[+A1, +B1]
//      trait KT2[+A2, +B2] extends KT1[B2, A2]
//      assertNotChild(LTT[KT2[H1, I1]], LTT[KT1[H1, I1]])
//      type T1[A] = W3[A] with W1
//      type T2[A] = W4[A] with W2
//      assertChild(`LTT[_]`[T2], `LTT[_]`[T1])


      trait KK1[+A, +B, +U]
      trait KK2[+A, +B] extends KK1[B, A, Unit]

      assertChild(LTT[KK2[Int, String]], LTT[KK1[String, Int, Unit]])

      assertChild(LTT[KK2[H2, I2]], LTT[KK1[I1, H1, Unit]])
      assertNotChild(LTT[KK2[H2, I2]], LTT[KK1[H1, I1, Unit]])

      assertChild(`LTT[_]`[KK2[H2, ?]], `LTT[_]`[KK1[?, H1, Unit]])
      assertNotChild(`LTT[_]`[KK2[H2, ?]], `LTT[_]`[KK1[H1, ?, Unit]])
    }

    "support subtype checks" in {
      assertChild(LTT[Int], LTT[AnyVal])
      assertChild(LTT[Int], LTT[Int])
      assertChild(LTT[List[Int]], LTT[List[Int]])
      assertChild(LTT[List[I2]], LTT[List[I1]])
      assertChild(LTT[Either[Nothing, Int]], LTT[Either[Throwable, Int]])

      assertChild(LTT[F2[I2]], LTT[F1[I1]])
      assertChild(LTT[FT2[IT2]], LTT[FT1[IT1]])
      assertChild(`LTT[_[_[_]]]`[FT2].combine(`LTT[_[_]]`[IT2]), LTT[FT1[IT1]])

      assertChild(LTT[FT2[IT2]], LTT[FT1[IT2]])

      assertChild(LTT[List[Int]], `LTT[_]`[List])
      assertNotChild(LTT[Set[Int]], `LTT[_]`[Set])

      assertChild(LTT[FM2[I2]], LTT[FM1[I1, Unit]])
      assertChild(LTT[FM2[I2]], `LTT[_,_]`[FM1])
      assertChild(LTT[Option[Nothing]], LTT[Option[Int]])
      assertChild(LTT[None.type], LTT[Option[Int]])

      assertChild(LTT[Option[W2]], LTT[Option[_ <: W1]])
      assertNotChild(LTT[Option[W2]], LTT[Option[_ <: I1]])


      assertChild(LTT[Option[H3]], LTT[Option[_ >: H4 <: H2]])
      assertNotChild(LTT[Option[H1]], LTT[Option[_ >: H4 <: H2]])

      // bottom boundary is weird!
      assertChild(LTT[Option[H5]], LTT[Option[_ >: H4 <: H2]])

      // I consider this stuff practically useless
      type X[A >: H4 <: H2] = Option[A]
      assertNotChild(LTT[Option[H5]], `LTT[A,B,_>:B<:A]`[H2, H4, X])
      assertChild(LTT[Option[H3]], `LTT[A,B,_>:B<:A]`[H2, H4, X])
    }


    "progression: support swapped parents" in {
      trait KT1[+A1, +B1]
      trait KT2[+A2, +B2] extends KT1[B2, A2]

      assertChild(LTT[KT2[H1, I1]], LTT[KT1[I1, H1]])
      assertNotChild(LTT[KT2[H1, I1]], LTT[KT1[H1, I1]])

      assertChild(LTT[KT2[H2, I2]], LTT[KT1[I1, H1]])
      assertNotChild(LTT[KT2[H2, I2]], LTT[KT1[H1, I1]])

      trait KK1[+A, +B, +U]
      trait KK2[+A, +B] extends KK1[B, A, Unit]

      assertChild(LTT[KK2[Int, String]], LTT[KK1[String, Int, Unit]])

      assertChild(LTT[KK2[H2, I2]], LTT[KK1[I1, H1, Unit]])
      assertNotChild(LTT[KK2[H2, I2]], LTT[KK1[H1, I1, Unit]])

      assertChild(`LTT[_]`[KK2[H2, ?]], `LTT[_]`[KK1[?, H1, Unit]])
      assertNotChild(`LTT[_]`[KK2[H2, ?]], `LTT[_]`[KK1[H1, ?, Unit]])
    }


    "support PDTs" in {
      val a = new C {
        override type A = Int
      }

      assertSame(LTT[a.A], LTT[Int])

      val a1: C = new C {
        override type A = Int
      }
      val a2: C = new C {
        override type A = String
      }

      assertDifferent(LTT[a1.A], LTT[Int])
      assertDifferent(LTT[a1.A], LTT[a2.A])
    }

    "support complex type lambdas" in {
      assertSame(`LTT[_,_]`[NestedTL[Const, ?, ?]], `LTT[_,_]`[Lambda[(A, B) => FM2[(B, A)]]])
      assertSame(`LTT[_[_]]`[NestedTL2[W1, W2, ?[_]]], `LTT[_[_]]`[Lambda[G[_] => FM2[G[S[W2, W1]]]]])
      assertChild(`LTT[_,_]`[NestedTL[Const, ?, ?]], `LTT[_,_]`[Lambda[(A, B) => FM2[(B, A)]]])
    }

    "support TagK* family summoners" in {
      assertSame(LTagK[List].fullLightTypeTag, `LTT[_]`[List])
    }

    "support intersection type equality" in {
      type T1[A] = W3[A] with W1
      type T2[A] = W4[A] with W2

      assertSame(`LTT[_]`[T1], `LTT[_]`[T1])
      assertDifferent(`LTT[_]`[T1], `LTT[_]`[T2])
    }

    "support intersection type subtype checks" in {
      type F1 = W3[Int] with W1
      type F2 = W4[Int] with W2

      type T1[A] = W3[A] with W1
      type T2[A] = W4[A] with W2

      assertChild(LTT[F1], LTT[W3[Int]])
      assertChild(LTT[F1], LTT[W1])
      assertChild(LTT[F2], LTT[F1])


      assertChild(`LTT[_]`[W4], `LTT[_]`[W3])
      assertChild(`LTT[_]`[T1], `LTT[_]`[W3])
      assertChild(`LTT[_]`[T1], LTT[W1])
      assertChild(`LTT[_]`[T2], `LTT[_]`[T1])
    }

    "support structural & refinement type equality" in {
      type C1 = C
      assertSame(LTT[ {def a: Int}], LTT[ {def a: Int}])
      assertSame(LTT[C {def a: Int}], LTT[C1 {def a: Int}])

      assertDifferent(LTT[C {def a: Int}], LTT[ {def a: Int}])
      assertDifferent(LTT[C {def a: Int}], LTT[C])

      assertDifferent(LTT[C {def a: Int}], LTT[C {def a: Int; def b: Int}])

      val a1 = new C {
        override type A = Int
      }
      object Z {
        type X <: {type A = Int}
      }
      Z.discard()

      assertSame(LTT[a1.A], LTT[Z.X#A])
    }

    "support structural & refinement type subtype checks" in {
      type C1 = C
      assertChild(LTT[ {def a: Int}], LTT[ {def a: Int}])
      assertChild(LTT[C {def a: Int}], LTT[C1 {def a: Int}])


      assertChild(LTT[C {def a: Int}], LTT[C])
      assertNotChild(LTT[C], LTT[C {def a: Int}])

      assertChild(LTT[C {def a: Int; def b: Int}], LTT[C {def a: Int}])
      assertNotChild(LTT[C {def a: Int}], LTT[C {def a: Int; def b: Int}])

      assertChild(LTT[C {def a: Int}], LTT[ {def a: Int}])
    }


    "resolve concrete types through PDTs and projections" in {
      val a1 = new C {
        override type A <: Int
      }
      object Z {
        type X <: {type A = Int}
      }
      Z.discard()

      assertChild(LTT[a1.A], LTT[Z.X#A])
      assertNotChild(LTT[Z.X#A], LTT[a1.A])
    }

  }
}
