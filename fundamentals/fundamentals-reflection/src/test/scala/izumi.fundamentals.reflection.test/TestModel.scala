package izumi.fundamentals.reflection.test

import scala.annotation.StaticAnnotation

object TestModel {
  final class IdAnnotation(val name: String) extends StaticAnnotation

  trait YieldOpCounts {
    def zioYieldOpCount: Int = 1024
    def blockingYieldOpCount: Int = Int.MaxValue
  }
  object YieldOpCounts extends YieldOpCounts

  trait T0[A[_], B[_]]
  final val str = "str"

  type Id[T] = T
  type FP1[+T] = List[T]
  type Ap1[+F[+_], +T] = F[T]
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

  trait R0[K, A <: R0[K, A]]
  trait R1[K] extends R0[K, R1[K]]

  type S[A, B] = Either[B, A]

  trait W1

  trait W2 extends W1

  trait W3[_]

  trait W4[A] extends W3[A]

  trait I1

  trait I2 extends I1

  trait F1[+A]
  trait F2[+A] extends F1[A]
  trait F3 extends F2[Int]

  trait FT1[+A[+_[+_]]]

  trait FT2[+A[+_[+_]]] extends FT1[A]

  trait IT1[+K[+_]]

  trait IT2[+K[+_]] extends IT1[K]

  trait FM1[+A, +B]

  trait FM2[+A] extends FM1[A, Unit]

  type NestedTL[G[_, _], A, B] = FM2[G[A, (B, A)]]

  type NestedTL2[A, B, G[_]] = FM2[G[S[B, A]]]

  type Const[A, B] = B

  type XS <: { type X }
  type WithX = { type X }

  trait H1
  trait H2 extends H1
  trait H3 extends H2
  trait H4 extends H3
  trait H5 extends H4

  trait J1[F[_]]
  trait J2
  trait J3
  trait J[F[_]] extends J1[F] with J2 with J3

  object TPrefix {
    type T
  }

  trait P0[A[_], B[_]]
  trait P1[A[_], B[_]] extends P0[B, A]
  trait X1[_]
  trait X2[_]

  trait XP1[A[_]] extends P0[X2, A]

  trait RoleParent[F[_]]
  trait RoleChild[F[_, _]] extends RoleParent[F[Throwable, ?]]
  class RoleChild2[F[+_, +_], A, B] extends RoleParent[F[Throwable, ?]]

  class ApplePaymentProvider[F[_]] extends H1

  trait ZIO[-R, +E, +A]
  type IO[+E, +A] = ZIO[Any, E, A]

  class BlockingIO3[F[_, _, _]]
  type BlockingIO[F[_, _]] = BlockingIO3[Lambda[(R, E, A) => F[E, A]]]
}
