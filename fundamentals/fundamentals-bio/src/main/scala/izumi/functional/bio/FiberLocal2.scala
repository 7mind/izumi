package izumi.functional.bio

import cats.effect.IOLocal
import izumi.functional.bio.data.~>
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.{InteropTracer, Tracer}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{FiberRef, ZIO}

trait FiberLocal2[F[_, _], A] {
  def get: F[Nothing, A]
  def locally[E, B](a: A)(f: => F[E, B]): F[E, B]
  def locallyWith[E, B](modification: A => A)(f: => F[E, B]): F[E, B]
}

trait FiberRef2[F[_, _], A] extends FiberLocal2[F, A] {
  def set(a: A): F[Nothing, Unit]
  def update(f: A => A): F[Nothing, A]
  def update_(f: A => A): F[Nothing, Unit]
  def modify[B](f: A => (B, A)): F[Nothing, B]
}

object FiberRef2 {
  def fromZIO[R, A](ref: FiberRef[A]): FiberRef2[ZIO[R, +_, +_], A] =
    new FiberRef2[ZIO[R, +_, +_], A] {
      override def get: ZIO[R, Nothing, A] = ref.get(Tracer.newTrace)
      override def locally[E, B](a: A)(f: => ZIO[R, E, B]): ZIO[R, E, B] = ref.locally(a)(f)(InteropTracer.newTrace(() => f))
      override def locallyWith[E, B](modification: A => A)(f: => ZIO[R, E, B]): ZIO[R, E, B] = ref.locallyWith(modification)(f)(InteropTracer.newTrace(() => f))

      override def set(a: A): ZIO[R, Nothing, Unit] = ref.set(a)(Tracer.newTrace)
      override def update(f: A => A): ZIO[R, Nothing, A] = ref.updateAndGet(f)(InteropTracer.newTrace(f))
      override def update_(f: A => A): ZIO[R, Nothing, Unit] = ref.update(f)(InteropTracer.newTrace(f))
      override def modify[B](f: A => (B, A)): ZIO[R, Nothing, B] = ref.modify(f)(InteropTracer.newTrace(f))

      disableAutoTrace.discard()
    }

  def fromCatsIOLocal[F[+_, +_]: Panic2, A](fromIO: cats.effect.IO ~> F[Throwable, _])(ioLocal: IOLocal[A]): FiberRef2[F, A] = {
    new FiberRef2[F, A] {
      override def get: F[Nothing, A] = fromIO(ioLocal.get).orTerminate
      override def locally[E, B](a: A)(f: => F[E, B]): F[E, B] = {
        F.bracket(acquire = get)(release = oldValue => set(oldValue))(use = _ => f)
      }
      override def locallyWith[E, B](modification: A => A)(f: => F[E, B]): F[E, B] = {
        F.bracket(acquire = modify[A](oldValue => (oldValue, modification(oldValue))))(release = oldValue => set(oldValue))(use = _ => f)
      }

      override def set(a: A): F[Nothing, Unit] = fromIO(ioLocal.set(a)).orTerminate
      override def update(f: A => A): F[Nothing, A] = fromIO(ioLocal.modify(a => { val b = f(a); (b, b) })).orTerminate
      override def update_(f: A => A): F[Nothing, Unit] = fromIO(ioLocal.update(f)).orTerminate
      override def modify[B](f: A => (B, A)): F[Nothing, B] = fromIO(ioLocal.modify(f(_).swap)).orTerminate
    }
  }
}
