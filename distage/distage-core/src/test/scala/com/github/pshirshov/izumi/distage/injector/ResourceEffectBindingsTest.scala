package com.github.pshirshov.izumi.distage.injector

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.injector.ResourceEffectBindingsTest.{IntSuspend, Ref, Res, SimpleResource, Suspend2, SuspendResource}
import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.{DIKey, Id, ModuleDef, PlannerInput}
import org.scalatest.WordSpec

class ResourceEffectBindingsTest extends WordSpec with MkInjector {

  final type Fn[+A] = Suspend2[Nothing, A]
  final type Ft[+A] = Suspend2[Throwable, A]

  "Effect bindings" should {

    "work in a basic case in Identity monad" in {
      val definition = PlannerInput(new ModuleDef {
        make[Int].named("2").from(2)
        make[Int].fromEffect[Identity, Int] { i: Int @Id("2") => 10 + i }
      }, roots = DIKey.get[Int])

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produceUnsafe(plan)

      assert(context.get[Int] == 12)
    }

    "work in a basic case in Suspend2 monad" in {
      val definition = PlannerInput(new ModuleDef {
        make[Int].named("2").from(2)
        make[Int].fromEffect { i: Int @Id("2") => Suspend2(10 + i) }
      }, roots = DIKey.get[Int])

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produceUnsafeF[Suspend2[Throwable, ?]](plan).unsafeRun()

      assert(context.get[Int] == 12)
    }

    "work with constructor binding" in {
      val definition = PlannerInput(new ModuleDef {
        make[Int].named("2").from(2)
        make[Int].fromEffect[Suspend2[Nothing, ?], Int, IntSuspend]
      }, roots = DIKey.get[Int])

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produceUnsafeF[Suspend2[Nothing, ?]](plan).unsafeRun()

      assert(context.get[Int] == 12)
    }

    "execute effects again in reference bindings" in {
      val execIncrement = (_: Ref[Fn, Int]).update(_ + 1)

      val definition = PlannerInput(new ModuleDef {
        make[Ref[Fn, Int]].fromEffect(Ref[Fn](0))

        make[Fn[Int]].from(execIncrement)

        make[Int].named("1").refEffect[Fn, Int]
        make[Int].named("2").refEffect[Fn, Int]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produceUnsafeF[Suspend2[Nothing, ?]](plan).unsafeRun()

      assert(context.get[Int]("1") == 1)
      assert(context.get[Int]("2") == 2)
      assert(context.get[Ref[Fn, Int]].get.unsafeRun() == 2)
    }

    "Support self-referencing circular effects" in {
      import com.github.pshirshov.izumi.distage.fixtures.CircularCases.CircularCase3._

      val definition = PlannerInput(new ModuleDef {
        make[Ref[Fn, Boolean]].fromEffect(Ref[Fn](false))
        make[SelfReference].fromEffect {
          (ref: Ref[Fn, Boolean], self: SelfReference) =>
            ref.update(!_).flatMap(_ => Suspend2(new SelfReference(self)))
        }
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produceUnsafeF[Suspend2[Throwable, ?]](plan).unsafeRun()

      val instance = context.get[SelfReference]

      assert(instance eq instance.self)
      assert(context.get[Ref[Fn, Boolean]].get.unsafeRun())
    }

    "support Identity effects in Suspend monad" in {
      val definition = PlannerInput(new ModuleDef {
        make[Int].named("2").from(2)
        make[Int].fromEffect[Identity, Int] { i: Int @Id("2") => 10 + i }
      }, roots = DIKey.get[Int])

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produceUnsafeF[Suspend2[Throwable, ?]](plan).unsafeRun()

      assert(context.get[Int] == 12)
    }

    "work with set bindings" in {
      val definition = PlannerInput(new ModuleDef {
        make[Ref[Fn, Set[Char]]].fromEffect(Ref[Fn](Set.empty[Char]))

        many[Char]
          .addEffect(Suspend2('a'))
          .addEffect(Suspend2('b'))

        make[Unit].fromEffect {
          (ref: Ref[Fn, Set[Char]], set: Set[Char]) =>
            ref.update(_ ++ set).void
        }
        make[Unit].named("1").fromEffect {
          ref: Ref[Fn, Set[Char]] =>
            ref.update(_ + 'z').void
        }
        make[Unit].named("2").fromEffect {
          (_: Unit, _: Unit @Id("1"), ref: Ref[Fn, Set[Char]]) =>
            ref.update(_.map(_.toUpper)).void
        }
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produceUnsafeF[Suspend2[Throwable, ?]](plan).unsafeRun()

      assert(context.get[Set[Char]] == "ab".toSet)
      assert(context.get[Ref[Fn, Set[Char]]].get.unsafeRun() == "ABZ".toSet)
    }

  }

  "Resource bindings" should {

    "work in a basic case in Identity monad" in {
      val definition = PlannerInput(new ModuleDef {
        make[Res].fromResource[SimpleResource]// FIXME: syntax
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val instance = injector.produce(plan).use {
        context =>
          val instance = context.get[Res]
          assert(instance.initialized)
          instance
      }

      assert(!instance.initialized)
    }

    "work in a basic case in Suspend2 monad" in {
      val definition = PlannerInput(new ModuleDef {
        make[Res].fromResource[SuspendResource]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val instance = injector.produceF[Suspend2[Throwable, ?]](plan).use {
        context =>
          val instance = context.get[Res]
          assert(instance.initialized)
          Suspend2(instance)
      }.unsafeRun()

      assert(!instance.initialized)
    }

    // FIXME: order test, ops accumulator

  }

}

object ResourceEffectBindingsTest {
  class Res {
    var initialized: Boolean = false
  }

  class SimpleResource extends DIResource.Simple[Res] {
    override def allocate: Res = { val x = new Res; x.initialized = true; x }
    override def deallocate(resource: Res): Unit = { resource.initialized = false }
  }

  class SuspendResource extends DIResource[Suspend2[Nothing, ?], Res] {
    override def allocate: Suspend2[Nothing, Res] = Suspend2(new Res).flatMap(r => Suspend2(r.initialized = true).map(_ => r))
    override def deallocate(resource: Res): Suspend2[Nothing, Unit] = Suspend2(resource.initialized = false)
  }

  class MutResource extends DIResource.Mutable[MutResource] {
    var init: Boolean = false
    override def allocate: Unit = { init = true }
    override def close(): Unit = ()
  }

  class IntSuspend(i: Int @Id("2")) extends Suspend2(() => Right(10 + i))

  class Ref[F[_]: DIEffect, A](r: AtomicReference[A]) {
    def get: F[A] = DIEffect[F].maybeSuspend(r.get())
    def update(f:  A => A): F[A] = DIEffect[F].maybeSuspend(r.updateAndGet(f(_)))
  }

  object Ref {
    def apply[F[_]]: Apply[F] = new Apply[F]()

    final class Apply[F[_]](private val dummy: Boolean = false) extends AnyVal {
      def apply[A](a: A)(implicit F: DIEffect[F]): F[Ref[F, A]] = {
        DIEffect[F].maybeSuspend(new Ref[F, A](new AtomicReference(a)))
      }
    }
  }

  case class Suspend2[+E, +A](run: () => Either[E, A]) {
    def map[B](g: A => B): Suspend2[E, B] = {
      Suspend2(() => run().map(g))
    }
    def flatMap[E1 >: E, B](g: A => Suspend2[E1, B]): Suspend2[E1, B] = {
      Suspend2(() => run().flatMap(g(_).run()))
    }
    def void: Suspend2[E, Unit] = map(_ => ())

    def unsafeRun(): A = run() match {
      case Left(value: Throwable) => throw value
      case Left(value) => throw new RuntimeException(value.toString)
      case Right(value) => value
    }
  }
  object Suspend2 {
    def apply[A](a: => A)(implicit dummyImplicit: DummyImplicit): Suspend2[Nothing, A] = new Suspend2(() => Right(a))

    implicit def dimonadSuspend2[E <: Throwable]: DIEffect[Suspend2[E, ?]] = new DIEffect[Suspend2[E, ?]] {
      override def flatMap[A, B](fa: Suspend2[E, A])(f: A => Suspend2[E, B]): Suspend2[E, B] = fa.flatMap(f)
      override def map[A, B](fa: Suspend2[E, A])(f: A => B): Suspend2[E, B] = fa.map(f)
      override def pure[A](a: A): Suspend2[E, A] = Suspend2(a)
      override def maybeSuspend[A](eff: => A): Suspend2[E, A] = Suspend2(eff)
      override def definitelyRecover[A](fa: => Suspend2[E, A], recover: Throwable => Suspend2[E, A]): Suspend2[E, A] = {
        Suspend2(() => fa.run() match {
          case Left(value) => recover(value).run()
          case Right(value) => Right(value)
        })
      }

      override def bracket[A, B](acquire: => Suspend2[E, A])(release: A => Suspend2[E, Unit])(use: A => Suspend2[E, B]): Suspend2[E, B] = {
        acquire.flatMap {
          a => definitelyRecover(
            use(a).flatMap(b => release(a).map(_ => b))
          , fail => release(a).flatMap(_ => maybeSuspend(throw fail))
          )
        }
      }
    }
  }
}
