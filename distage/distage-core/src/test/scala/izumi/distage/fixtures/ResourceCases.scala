package izumi.distage.fixtures

import java.util.concurrent.atomic.AtomicReference

import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.fundamentals.platform.language.Quirks._

import scala.collection.immutable.Queue
import scala.collection.mutable
import scala.util.Try

object ResourceCases {

  object ResourceCase1 {
    sealed trait Ops
    case object XStart extends Ops
    case object XStop extends Ops
    case object YStart extends Ops
    case object YStop extends Ops
    case object ZStop extends Ops

    class X
    class Y
    class Z

    val queueEffect = Suspend2(mutable.Queue.empty[Ops])

    class XResource(queue: mutable.Queue[Ops]) extends Lifecycle[Suspend2[Nothing, ?], X] {
      override def acquire: Suspend2[Nothing, X] = Suspend2 {
        queue += XStart
        new X
      }

      override def release(resource: X): Suspend2[Nothing, Unit] = Suspend2 {
        resource.discard()

        queue += XStop
      }.void
    }

    class YResource(x: X, queue: mutable.Queue[Ops]) extends Lifecycle[Suspend2[Nothing, ?], Y] {
      x.discard()

      override def acquire: Suspend2[Nothing, Y] = Suspend2 {
        queue += YStart
        new Y
      }

      override def release(resource: Y): Suspend2[Nothing, Unit] = Suspend2 {
        resource.discard()

        queue += YStop
      }.void
    }

    class ZFaultyResource(y: Y) extends Lifecycle[Suspend2[Throwable, ?], Z] {
      y.discard()

      override def acquire: Suspend2[Throwable, Z] = throw new RuntimeException()
      override def release(resource: Z): Suspend2[Throwable, Unit] = throw new RuntimeException()
    }
  }

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

    def s3ComponentResource[F[_]: DIEffect](ref: Ref[F, Queue[Ops]], s3Client: S3Client): Lifecycle[F, S3Component] =
      Lifecycle.make(
        acquire = ref.update(_ :+ ComponentStart).map(_ => new S3Component(s3Client))
      )(release = _ => ref.update(_ :+ ComponentStop).map(_ => ()))

    def s3clientResource[F[_]: DIEffect](ref: Ref[F, Queue[Ops]], s3Component: S3Component): Lifecycle[F, S3ClientImpl] =
      Lifecycle.make(
        acquire = ref.update(_ :+ ClientStart).map(_ => new S3ClientImpl(s3Component))
      )(release = _ => ref.update(_ :+ ClientStop).map(_ => ()))

  }

  object ClassResourceCase {

    class Res {
      var initialized: Boolean = false
    }

    class SimpleResource extends Lifecycle.Simple[Res] {
      override def acquire: Res = {
        val x = new Res; x.initialized = true; x
      }

      override def release(resource: Res): Unit = {
        resource.initialized = false
      }
    }

    class SuspendResource extends Lifecycle[Suspend2[Nothing, ?], Res] {
      override def acquire: Suspend2[Nothing, Res] = Suspend2(new Res).flatMap(r => Suspend2(r.initialized = true).map(_ => r))

      override def release(resource: Res): Suspend2[Nothing, Unit] = Suspend2(resource.initialized = false)
    }

  }

  class MutResource extends Lifecycle.Mutable[MutResource] {
    var init: Boolean = false
    override def acquire: Unit = { init = true }
    override def release: Unit = ()
  }

  class Ref[F[_]: DIEffect, A](r: AtomicReference[A]) {
    def get: F[A] = DIEffect[F].maybeSuspend(r.get())
    def update(f: A => A): F[A] = DIEffect[F].maybeSuspend(r.synchronized { r.set(f(r.get())); r.get() }) // no `.updateAndGet` on scala.js...
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
    def apply[A](a: => A)(implicit dummy: DummyImplicit): Suspend2[Nothing, A] = new Suspend2(() => Right(a))

    implicit def DIEffectSuspend2[E <: Throwable]: DIEffect[Suspend2[E, ?]] = new DIEffect[Suspend2[E, ?]] {
      override def flatMap[A, B](fa: Suspend2[E, A])(f: A => Suspend2[E, B]): Suspend2[E, B] = fa.flatMap(f)
      override def map[A, B](fa: Suspend2[E, A])(f: A => B): Suspend2[E, B] = fa.map(f)
      override def map2[A, B, C](fa: Suspend2[E, A], fb: => Suspend2[E, B])(f: (A, B) => C): Suspend2[E, C] = fa.flatMap(a => fb.map(f(a, _)))
      override def pure[A](a: A): Suspend2[E, A] = Suspend2(a)
      override def fail[A](t: => Throwable): Suspend2[E, A] = Suspend2[A](throw t)
      override def maybeSuspend[A](eff: => A): Suspend2[E, A] = Suspend2(eff)
      override def definitelyRecover[A](fa: => Suspend2[E, A])(recover: Throwable => Suspend2[E, A]): Suspend2[E, A] = {
        Suspend2(
          () =>
            Try(fa.run()).toEither.flatMap(identity) match {
              case Left(exception) => recover(exception).run()
              case Right(value) => Right(value)
            }
        )
      }
      override def definitelyRecoverCause[A](action: => Suspend2[E, A])(recoverCause: (Throwable, () => Throwable) => Suspend2[E, A]): Suspend2[E, A] = {
        definitelyRecover(action)(e => recoverCause(e, () => e))
      }

      override def bracket[A, B](acquire: => Suspend2[E, A])(release: A => Suspend2[E, Unit])(use: A => Suspend2[E, B]): Suspend2[E, B] =
        bracketCase(acquire) { case (a, _) => release(a) }(use)

      override def bracketCase[A, B](acquire: => Suspend2[E, A])(release: (A, Option[Throwable]) => Suspend2[E, Unit])(use: A => Suspend2[E, B]): Suspend2[E, B] = {
        acquire.flatMap {
          a =>
            definitelyRecover(use(a)) {
              err =>
                release(a, Some(err)).flatMap(_ => fail(err))
            }.flatMap(res => release(a, None).map(_ => res))
        }
      }
    }
  }
}
