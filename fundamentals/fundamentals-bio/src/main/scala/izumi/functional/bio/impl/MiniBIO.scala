package izumi.functional.bio.impl

import izumi.functional.bio.Exit.Trace
import izumi.functional.bio.data.{Morphism2, RestoreInterruption2}
import izumi.functional.bio.impl.MiniBIO.Fail
import izumi.functional.bio.{BlockingIO2, Exit, IO2}

import scala.annotation.{nowarn, tailrec}
import scala.language.implicitConversions

/**
  * A lightweight dependency-less implementation of the [[izumi.functional.bio.IO2]] interface,
  * analogous in purpose with [[cats.effect.kernel.SyncIO]]
  *
  * Sync-only, not interruptible, not async.
  * For internal use. Prefer ZIO or cats-bio in production.
  *
  * This is safe to run in a synchronous environment,
  * Use MiniBIO.autoRun to gain access to components polymorphic over [[izumi.functional.bio.IO2]]
  * in a synchronous environment.
  *
  * {{{
  *   final class MyBIOClock[F[+_, +_]: IO2]() {
  *     val nanoTime: F[Nothing, Long] = IO2(System.nanoTime())
  *   }
  *
  *   import MiniBIO.autoRun._
  *
  *   val time: Long = new MyBIOClock().nanoTime
  *   println(time)
  * }}}
  */
sealed trait MiniBIO[+E, +A] {
  final def run(): Exit[E, A] = {

    final class Catcher[E0, A0, E1, B](
      val recover: Exit.Failure[E0] => MiniBIO[E1, B],
      f: A0 => MiniBIO[E1, B],
    ) extends (A0 => MiniBIO[E1, B]) {
      override def apply(a: A0): MiniBIO[E1, B] = f(a)
    }

    // FIXME: Scala 3.1.4 bug: false unexhaustive match warning
    @nowarn("msg=pattern case: MiniBIO.FlatMap")
    @tailrec def runner(op: MiniBIO[Any, Any], stack: List[Any => MiniBIO[Any, Any]]): Exit[Any, Any] = op match {

      case MiniBIO.FlatMap(io, f) =>
        runner(io, f.asInstanceOf[Any => MiniBIO[Any, Any]] :: stack)

      case MiniBIO.Redeem(io, err, succ) =>
        runner(io, new Catcher(err, succ).asInstanceOf[Any => MiniBIO[Any, Any]] :: stack)

      case MiniBIO.Sync(a) =>
        val exit =
          try { a() }
          catch {
            case t: Throwable =>
              Exit.Termination(t, Trace.ThrowableTrace(t))
          }
        exit match {
          case Exit.Success(value) =>
            stack match {
              case flatMap :: stackRest =>
                val nextIO =
                  try { flatMap(value) }
                  catch {
                    case t: Throwable =>
                      Fail.terminate(t)
                  }
                runner(nextIO, stackRest)

              case Nil =>
                exit
            }

          case failure: Exit.Failure[?] =>
            runner(Fail.halt(failure), stack)
        }
      case MiniBIO.Fail(e) =>
        val err =
          try e()
          catch {
            case t: Throwable =>
              Exit.Termination(t, Trace.ThrowableTrace(t))
          }
        val catcher = stack.dropWhile(!_.isInstanceOf[Catcher[?, ?, ?, ?]])
        catcher match {
          case value :: stackRest =>
            runner(value.asInstanceOf[Catcher[Any, Any, Any, Any]].recover(err), stackRest)

          case Nil =>
            err
        }
    }

    runner(this, Nil).asInstanceOf[Exit[E, A]]
  }
}

object MiniBIO {
  object autoRun {
    implicit def autoRunAlways[A](f: MiniBIO[Throwable, A]): A = f.run() match {
      case Exit.Success(value) =>
        value
      case failure: Exit.Failure[Throwable] =>
        throw failure.toThrowable
    }

    implicit def BIOMiniBIOHighPriority: IO2[MiniBIO] = BIOMiniBIO
  }

  final case class Fail[+E](e: () => Exit.Failure[E]) extends MiniBIO[E, Nothing]
  object Fail {
    def terminate(t: Throwable): Fail[Nothing] = Fail(() => Exit.Termination(t, Trace.ThrowableTrace(t)))
    def halt[E](e: => Exit.Failure[E]): Fail[E] = Fail(() => e)
  }
  final case class Sync[+E, +A](a: () => Exit[E, A]) extends MiniBIO[E, A]
  final case class FlatMap[E, A, +E1 >: E, +B](io: MiniBIO[E, A], f: A => MiniBIO[E1, B]) extends MiniBIO[E1, B]
  final case class Redeem[E, A, +E1, +B](io: MiniBIO[E, A], err: Exit.Failure[E] => MiniBIO[E1, B], succ: A => MiniBIO[E1, B]) extends MiniBIO[E1, B]

  implicit val BIOMiniBIO: IO2[MiniBIO] & BlockingIO2[MiniBIO] = new IO2[MiniBIO] with BlockingIO2[MiniBIO] {
    override def pure[A](a: A): MiniBIO[Nothing, A] = sync(a)
    override def flatMap[E, A, B](r: MiniBIO[E, A])(f: A => MiniBIO[E, B]): MiniBIO[E, B] = FlatMap(r, f)
    override def fail[E](v: => E): MiniBIO[E, Nothing] = Fail(() => Exit.Error(v, Trace.forTypedError(v)))
    override def terminate(v: => Throwable): MiniBIO[Nothing, Nothing] = Fail.terminate(v)
    override def sendInterruptToSelf: MiniBIO[Nothing, Unit] = unit

    override def syncThrowable[A](effect: => A): MiniBIO[Throwable, A] = Sync {
      () =>
        try {
          Exit.Success(effect)
        } catch { case e: Throwable => Exit.Error(e, Trace.ThrowableTrace(e)) }
    }
    override def sync[A](effect: => A): MiniBIO[Nothing, A] = Sync(() => Exit.Success(effect))

    override def redeem[E, A, E2, B](r: MiniBIO[E, A])(err: E => MiniBIO[E2, B], succ: A => MiniBIO[E2, B]): MiniBIO[E2, B] = {
      Redeem[E, A, E2, B](
        r,
        {
          case e: Exit.Interruption => Fail.halt(e)
          case e: Exit.Termination => Fail.halt(e)
          case Exit.Error(e, _) => err(e)
        },
        succ,
      )
    }

    override def catchAll[E, A, E2](r: MiniBIO[E, A])(f: E => MiniBIO[E2, A]): MiniBIO[E2, A] = redeem(r)(f, pure)

    override def bracketCase[E, A, B](acquire: MiniBIO[E, A])(release: (A, Exit[E, B]) => MiniBIO[Nothing, Unit])(use: A => MiniBIO[E, B]): MiniBIO[E, B] = {
      // does not propagate error raised in release if `use` failed, in that case only error from `use` is preserved
      flatMap(acquire)(
        a =>
          Redeem[E, B, E, B](
            io = use(a),
            err = e => Redeem[Nothing, Unit, E, Nothing](release(a, e), err = _ => Fail(() => e), succ = _ => Fail(() => e)),
            succ = v => map(release(a, Exit.Success(v)))(_ => v),
          )
      )
    }

    override def sandbox[E, A](r: MiniBIO[E, A]): MiniBIO[Exit.Failure[E], A] = {
      Redeem[E, A, Exit.Failure[E], A](r, e => fail(e), pure)
    }

    override def traverse[E, A, B](l: Iterable[A])(f: A => MiniBIO[E, B]): MiniBIO[E, List[B]] = {
      val x = l.foldLeft(pure(Nil): MiniBIO[E, List[B]]) {
        (acc, a) =>
          flatMap(acc)(list => map(f(a))(_ :: list))
      }
      map(x)(_.reverse)
    }

    override def shiftBlocking[E, A](f: MiniBIO[E, A]): MiniBIO[E, A] = f

    override def syncBlocking[A](f: => A): MiniBIO[Throwable, A] = sync(f)

    override def syncInterruptibleBlocking[A](f: => A): MiniBIO[Throwable, A] = sync(f)
    override def uninterruptible[E, A](r: MiniBIO[E, A]): MiniBIO[E, A] = r
    override def uninterruptibleExcept[E, A](r: RestoreInterruption2[MiniBIO] => MiniBIO[E, A]): MiniBIO[E, A] = r(Morphism2(identity(_)))
    override def bracketExcept[E, A, B](
      acquire: RestoreInterruption2[MiniBIO] => MiniBIO[E, A]
    )(release: (A, Exit[E, B]) => MiniBIO[Nothing, Unit]
    )(use: A => MiniBIO[E, B]
    ): MiniBIO[E, B] = bracketCase(acquire(Morphism2(identity(_))))(release)(use)
  }
}
