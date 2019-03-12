package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.injector.ResourceEffectBindingsTest.Suspend2
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.Id
import distage.DIKey
import org.scalatest.WordSpec

class ResourceEffectBindingsTest extends WordSpec with MkInjector {

  "Effect bindings" should {

    "work in a basic case with Identity monad" in {
      val definition = new ModuleDef {
        make[Int].named("2").from(2)
        make[Int].fromEffect[Identity, Int] { i: Int @Id("2") => 10 + i }
      }

      val injector = mkInjector()
      val plan = injector.plan(definition, roots = Set(DIKey.get[Int]))
      val ctx = injector.produceUnsafe(plan)

      assert(ctx.get[Int] == 12)
    }

    "work in a basic case with Suspend2 monad" in {
      val definition = new ModuleDef {
        make[Int].named("2").from(2)
        make[Int].fromEffect { i: Int @Id("2") => Suspend2(10 + i) }
      }

      val injector = mkInjector()
      val plan = injector.plan(definition, roots = Set(DIKey.get[Int]))

      val f = injector.produce[Suspend2[Throwable, ?]](plan)
      val res = f.unsafeRun()

      assert(res.map(_.get[Int]) contains 12)
    }

  }

}

object ResourceEffectBindingsTest {
  final case class Suspend2[+E, +A](run: () => Either[E, A]) {
    def map[B](g: A => B): Suspend2[E, B] = {
      Suspend2(() => run().map(g))
    }
    def flatMap[E1 >: E, B](g: A => Suspend2[E1, B]): Suspend2[E1, B] = {
      Suspend2(() => run().flatMap(g(_).run()))
    }

    def unsafeRun(): A = run() match {
      case Left(value: Throwable) => throw value
      case Left(value) => throw new RuntimeException(value.toString)
      case Right(value) => value
    }
  }
  object Suspend2 {
    def apply[A](a: => A)(implicit dummyImplicit: DummyImplicit): Suspend2[Nothing, A] = new Suspend2(() => Right(a))

    implicit def dimonadSuspend2[E]: DIMonad[Suspend2[E, ?]] = new DIMonad[Suspend2[E, ?]] {
      override def flatMap[A, B](fa: Suspend2[E, A])(f: A => Suspend2[E, B]): Suspend2[E, B] = fa.flatMap(f)
      override def map[A, B](fa: Suspend2[E, A])(f: A => B): Suspend2[E, B] = fa.map(f)
      override def pure[A](a: A): Suspend2[E, A] = Suspend2(a)
    }
  }
}
