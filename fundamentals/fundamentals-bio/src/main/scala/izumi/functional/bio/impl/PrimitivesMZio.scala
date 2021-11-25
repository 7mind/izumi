package izumi.functional.bio.impl

import izumi.functional.bio.{Mutex2, PrimitivesM2, RefM2}
import zio.{IO, RefM, ZIO}

object PrimitivesMZio extends PrimitivesMZio

class PrimitivesMZio extends PrimitivesM2[IO] {
  override def mkRefM[A](a: A): IO[Nothing, RefM2[IO, A]] = {
    RefM.make(a).map(RefM2.fromZIO)
  }
  override def mkMutex[E, A]: IO[Nothing, Mutex2[IO]] = {
    object assertionFail {

      sealed trait Xa[T]
      sealed trait Mu[T] extends Xa[T]
      object Xa {
        implicit def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Xa[A]): X[B] = t.asInstanceOf[X[B]]
//        implicit def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Mu[A]): X[B] = t.asInstanceOf[X[B]]
      }
      object Mu {
        implicit def mu: Mu[Int] = new Mu[Int]
      }

      object App extends App {
        def constrain(a: Mu[Long]): Unit = println(a)
        constrain(Xa.convertMu)
      }

    }

    object shouldWork {
      sealed trait Xa[T]
      sealed trait Mu[T] extends Xa[T]
      object Xa extends XaLp {
        implicit def mu: Mu[Int] { type X } = new Mu[Int] { type X }
      }
      sealed trait XaLp {
        implicit def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A]): X[B] = t.asInstanceOf[X[B]]
      }

      object App extends App {
        def constrain(a: Mu[Long]): Unit = println(a)
        constrain(Xa.convertMu)
      }
    }

    sealed trait Xa[T]
    object Xa extends XaLp {
//      implicit def mu: Mu[Int] { type X } = new Mu[Int] { type X }
    }
    sealed trait Xa1[T] extends Xa[T]
    object Xa1 {
      implicit def mu: Mu[Int] { type X } = new Mu[Int] { type X }
    }
    sealed trait XaLp {
      // Bad
//      implicit def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A]): X[B] = t.asInstanceOf[X[B]]
//      def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A]): X[B] = t.asInstanceOf[X[B]]
      // Good
//      implicit def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Xa[A]): X[B] = t.asInstanceOf[X[B]]
      implicit def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Xa1[A]): X[B] = t.asInstanceOf[X[B]]
//      def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Xa[A]): X[B] = t.asInstanceOf[X[B]]
//      def convertMu[X[x] <: Xa[x], A, B, XA <: (X[A] & Xa[A]), XB >: (X[B] & Xa[B])](implicit t: XA): XB = t.asInstanceOf[XB]
//      def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Xa1[A]): X[B] = t.asInstanceOf[X[B]]
    }
    sealed trait Mu[T] extends Xa1[T]

    def main = {
      def constrain(a: Mu[Long]) = ()
      constrain(implicitly) // causes assertion failure with `implicit def convertMu`
      constrain(Xa.convertMu)
//      constrain(Xa1.convertMu)
//      constrain(Xa.convertMu[Mu, Int, Long])
    }
    def xo(f: izumi.functional.bio.Bracket2[IO]) = ()

    xo(izumi.functional.bio.Root.Convert3To2)

    Mutex2.createFromBIO(izumi.functional.bio.Root.Convert3To2 /*[izumi.functional.bio.Bracket3, ZIO, Any]*/, implicitly)
  }
}

//sealed trait Xa
//object Xa /*extends XaLp */ {
////      implicit def mu: Mu[Int] { type X } = new Mu[Int] { type X }
//}
//sealed trait Xa1 extends Xa
//object Xa1 extends XaLp {
//  implicit def xa1: Xa1 { type X } = new Xa1 { type X }
//}
//sealed trait XaLp {
//  // Bad
////      implicit def convertMu[X >: Y <: Xa, Y <: Xa](implicit t: X): Y = t.asInstanceOf[Y]
//  implicit def convertMu[X <: Xa, Y <: Xa](implicit t: X): Y = t.asInstanceOf[Y]
//  // Good
////  implicit def convertMu[X >: Y, Y <: Xa](implicit t: X): Y = t.asInstanceOf[Y]
////      implicit def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Xa[A]): X[B] = t.asInstanceOf[X[B]]
////      def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Xa[A]): X[B] = t.asInstanceOf[X[B]]
////      def convertMu[X[x] <: Xa[x], A, B](implicit t: X[A] & Xa1[A]): X[B] = t.asInstanceOf[X[B]]
//}
//sealed trait Mu extends Xa1
//
//@main def main = {
//  def constrain(a: Mu) = ()
////  constrain(implicitly) // causes assertion failure with `implicit def convertMu`
////      constrain(Xa.convertMu)
//  constrain(Xa1.convertMu)
////      constrain(Xa.convertMu[Mu, Int, Long])
//}
