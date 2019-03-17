package com.github.pshirshov.izumi.distage.fixtures

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import distage.Id

import scala.collection.immutable.Queue

object ResourceCases {

  object CircularResourceCase {

    sealed trait Ops { def invert: Ops }
    case object ComponentStart extends Ops { val invert = ComponentStop }
    case object ClientStart extends Ops { val invert = ClientStop }
    case object ComponentStop extends Ops { val invert = ComponentStart }
    case object ClientStop extends Ops { val invert = ClientStart }

    trait S3Client {
      def c: S3Component
    }
    trait IntegrationComponent

    class S3Component(val s: S3Client) extends IntegrationComponent
    class S3ClientImpl(val c: S3Component) extends S3Client

    def s3ComponentResource[F[_]: DIEffect](ref: Ref[F, Queue[Ops]], s3Client: S3Client): DIResource[F, S3Component] =
      DIResource.make(
        acquire = ref.update(_ :+ ComponentStart).map(_ => new S3Component(s3Client))
      )(release = _ => ref.update(_ :+ ComponentStop).map(_ => ()))

    def s3clientResource[F[_]: DIEffect](ref: Ref[F, Queue[Ops]], s3Component: S3Component): DIResource[F, S3ClientImpl] =
      DIResource.make(
        acquire = ref.update(_ :+ ClientStart).map(_ => new S3ClientImpl(s3Component))
      )(release = _ => ref.update(_ :+ ClientStop).map(_ => ()))

  }

  object ClassResourceCase {
    class Res {
      var initialized: Boolean = false
    }

    class SimpleResource extends DIResource.Simple[Res] {
      override def allocate: Res = {
        val x = new Res; x.initialized = true; x
      }

      override def deallocate(resource: Res): Unit = {
        resource.initialized = false
      }
    }

    class SuspendResource extends DIResource[Suspend2[Nothing, ?], Res] {
      override def allocate: Suspend2[Nothing, Res] = Suspend2(new Res).flatMap(r => Suspend2(r.initialized = true).map(_ => r))

      override def deallocate(resource: Res): Suspend2[Nothing, Unit] = Suspend2(resource.initialized = false)
    }

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
    def set(a: A): F[A] = update(_ => a)
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
