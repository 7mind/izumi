package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

import scala.language.higherKinds

@ExposedTestScope
object HigherKindCases {

  object HigherKindsCase1 {
    type id[A] = A

    trait Pointed[F[_]] {
      def point[A](a: A): F[A]
    }

    object Pointed {
      def apply[F[_]: Pointed]: Pointed[F] = implicitly

      implicit final val pointedList: Pointed[List] =
        new Pointed[List] {
          override def point[A](a: A): List[A] = List(a)
        }

      implicit final def pointedOptionT[F[_]: Pointed]: Pointed[OptionT[F, ?]] =
        new Pointed[OptionT[F, ?]] {
          override def point[A](a: A): OptionT[F, A] = OptionT(Pointed[F].point(Some(a)))
        }

      implicit final val pointedId: Pointed[id] =
        new Pointed[id] {
          override def point[A](a: A): id[A] = a
        }

    }

    case class OptionT[F[_], A](value: F[Option[A]])

    trait TestTrait {
      type R[_]

      def get: R[Int]
    }

    // TODO: @Id(this)
    class TestServiceClass[F[_]: Pointed](@Id("TestService") getResult: Int) extends TestTrait {
      override type R[_] = F[_]

      override def get: F[Int] = {
        Pointed[F].point(getResult)
      }
    }

    trait TestServiceTrait[F[_]] extends TestTrait {
      override type R[_] = F[_]

      implicit protected val pointed: Pointed[F]

      protected val getResult: Int @Id("TestService")

      override def get: F[_] = Pointed[F].point(getResult * 2)
    }

    abstract class TestProvider[A, F[_]: Pointed] {
      def f(a: A): F[A]
    }

    abstract class TestProvider0[A, B, F[_]: Pointed] {
      def f(a: A): F[A]
    }

    abstract class TestProvider1[A, G[_]: Pointed, F[_]: Pointed] {
      def f(a: A): F[A]
    }

    abstract class TestProvider2[G[_]: Pointed, F[_]: Pointed, A] {
      def f(a: A): F[A]
    }

    abstract class TestProvider3[A, B, C, F[_]: Pointed] {
      def f(a: A): F[A]
    }
  }

  object HigherKindsCase2 {

    class TestCovariantTC[F[+_, +_]]

    object TestCovariantTC {
      implicit def apply[F[+ _, + _]]: TestCovariantTC[F] = new TestCovariantTC[F]
    }

    class TestClassFA[F[+ _, + _]: TestCovariantTC, A]

    class TestClassFG[F[+ _, + _]: TestCovariantTC, G[_]]

  }

}
