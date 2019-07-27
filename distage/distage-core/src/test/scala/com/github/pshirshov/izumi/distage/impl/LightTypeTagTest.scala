package com.github.pshirshov.izumi.distage.impl

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

  trait W2

  trait W3[_]

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

  def println(o: Any) = info(o.toString)

  def println(o: FLTT) = info(o.t.toString)

  def assertRepr(t: FLTT, expected: String): Unit = {
    assert(t.toString == expected)
  }

  def assertCombine(outer: FLTT, inner: FLTT, expected: FLTT): Unit = {
    val combined = outer.combine(inner)
    info(s"($outer)($inner) => $combined ≈?≈ $expected")
    assert(combined == expected)
  }


  "lightweight type tags" should {
    "support human-readable representation" in {
      assertRepr(`LTT[_]`[R1], "λ %0 → R1[=0]")
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


      println("E")
      println(`LTT[_[_]]`[T0[F, ?[_]]])
      println(`LTT[_]`[FP])
      println(LTT[T2[T0]])

//      assert(`LTT[_[_]]`[T0[F, ?[_]]].combine(`LTT[_]`[FP]) == LTT[T0[F, FP]])
//      assert(`LTT[_[_]]`[T1].combine(`LTT[_]`[List]) == LTT[T1[List]])
//      assert(`LTT[_]`[List].combine(LTT[Int]) == LTT[List[Int]])

//      println(`LTT[_,_]`[Either])
//      assert(`LTT[_,_]`[Either].combine(LTT[Unit]) == `LTT[_]`[Either[Unit, ?]])
    }

    "support non-positional typetag combination" in {
      assert(`LTT[_,_]`[Either].combineNonPos(None, Some(LTT[Unit])) == `LTT[_]`[Either[?, Unit]])

    }

    "support subtype checks" in {
      assert(LTT[Int] <:< LTT[AnyVal])
      assert(LTT[Int] <:< LTT[Int])
      assert(LTT[List[Int]] <:< LTT[List[Int]])
      assert(LTT[List[I2]] <:< LTT[List[I1]])
      assert(LTT[Either[Nothing, Int]] <:< LTT[Either[Throwable, Int]])

      assert(LTT[F2[I2]] <:< LTT[F1[I1]])
      assert(LTT[FT2[IT2]] <:< LTT[FT1[IT1]])
      assert(`LTT[_[_[_]]]`[FT2].combine(`LTT[_[_]]`[IT2]) <:< LTT[FT1[IT1]])

      assert(LTT[FT2[IT2]] <:< LTT[FT1[IT2]])

      assert(LTT[List[Int]] <:< `LTT[_]`[List])
      assert(!(LTT[Set[Int]] <:< `LTT[_]`[Set]))

      assert(LTT[FM2[I2]] <:< LTT[FM1[I1, Unit]])
      assert(LTT[FM2[I2]] <:< `LTT[_,_]`[FM1])

    }

    "support PDTs" in {
      val a = new C {
        override type A = Int
      }

      assert(LTT[a.A] == LTT[Int])

      val a1: C = new C {
        override type A = Int
      }
      val a2: C = new C {
        override type A = String
      }

      assert(LTT[a1.A] != LTT[Int])
      assert(LTT[a1.A] == LTT[a2.A])
    }


    //    "support structural types" in {
    //
    //      println(LTT[{def a: Int}])
    //
    //    }
    //
    //    "support refinements" in {
    //      println(LTT[W1 with W2])
    //      type T[A] = W3[A] with W2
    //      println(`LTT[_]`[T])
    //    }

    //    "resolve concrete types through PDTs and projections" in {
    //      val a1 = new C {
    //        override type A = Int
    //      }
    //      type X = {type A = Int}
    //
    //      assert(LTT[a1.A] == LTT[X#A])
    //
    //
    //    }
  }
}
