package izumi.functional.bio.impl

import izumi.functional.bio.BIOExit.Trace
import izumi.functional.bio.impl.MiniBIO.Fail
import izumi.functional.bio.{BIO, BIOExit, BlockingIO}

import scala.annotation.tailrec
import scala.language.implicitConversions

/**
  * A lightweight dependency-less implementation of the [[BIO]] interface,
  * analogous in purpose with [[cats.effect.SyncIO]]
  *
  * Sync-only, not interruptible, not async.
  * For internal use. Prefer ZIO or cats-bio in production.
  *
  * This is safe to run in a synchronous environment,
  * Use MiniBIO.autoRun to gain access to components polymorphic over [[BIO]]
  * in a synchronous environment.
  *
  * {{{
  *   final class MyBIOClock[F[+_, +_]: BIO]() {
  *     val nanoTime: F[Nothing, Long] = BIO(System.nanoTime())
  *   }
  *
  *   import MiniBIO.autoRun._
  *
  *   val time: Long = new MyBIOClock().nanoTime
  *   println(time)
  * }}}
  * */
sealed trait MiniBIO[+E, +A] {
  final def run(): BIOExit[E, A] = {

    final class Catcher[E0, A0, E1, B](
                                        val recover: BIOExit.Failure[E0] => MiniBIO[E1, B]
                                      , f: A0 => MiniBIO[E1, B]
                                      ) extends (A0 => MiniBIO[E1, B]) {
      override def apply(a: A0): MiniBIO[E1, B] = f(a)
    }

    @tailrec def runner(op: MiniBIO[Any, Any], stack: List[Any => MiniBIO[Any, Any]]): BIOExit[Any, Any] = op match {

      case MiniBIO.FlatMap(io, f) =>
        runner(io, f :: stack)

      case MiniBIO.Redeem(io, err, succ) =>
        runner(io, new Catcher(err, succ) :: stack)

      case MiniBIO.Sync(a) =>
        val exit = try { a() } catch {
          case t: Throwable =>
            BIOExit.Termination(t, Trace.empty)
        }
        exit match {
          case BIOExit.Success(value) =>
            stack match {
              case flatMap :: stackRest =>
                val nextIO = try { flatMap(value) } catch {
                  case t: Throwable =>
                    Fail.terminate(t)
                }
                runner(nextIO, stackRest)

              case Nil =>
                exit
            }

          case failure: BIOExit.Failure[_] =>
            runner(Fail.halt(failure), stack)
        }
      case MiniBIO.Fail(e) =>
        val err = try e() catch {
          case t: Throwable =>
            BIOExit.Termination(t, Trace.empty)
        }
        val catcher = stack.dropWhile(!_.isInstanceOf[Catcher[_, _, _, _]])
        catcher match {
          case value :: stackRest =>
            runner(value.asInstanceOf[Catcher[Any, Any, Any, Any]].recover(err), stackRest)

          case Nil =>
            err
        }
    }

    runner(this, Nil).asInstanceOf[BIOExit[E, A]]
  }
}

object MiniBIO {

  object autoRun {
    implicit def autoRunAlways[A](f: MiniBIO[Throwable, A]): A = f.run() match {
      case BIOExit.Success(value) =>
        value
      case failure: BIOExit.Failure[Throwable] =>
        throw failure.toThrowable
    }

    implicit def BIOMiniBIOHighPriority: BIO[MiniBIO] = BIOMiniBIO
  }

  final case class Fail[+E](e: () => BIOExit.Failure[E]) extends MiniBIO[E, Nothing]
  object Fail {
    def terminate(t: Throwable): Fail[Nothing] = Fail(() => BIOExit.Termination(t, Trace.empty))
    def halt[E](e: => BIOExit.Failure[E]): Fail[E] = Fail(() => e)
  }
  final case class Sync[+E, +A](a: () => BIOExit[E, A]) extends MiniBIO[E, A]
  final case class FlatMap[E, A, +E1 >: E, +B](io: MiniBIO[E, A], f: A => MiniBIO[E1, B]) extends MiniBIO[E1, B]
  final case class Redeem[E, A, +E1, +B](io: MiniBIO[E, A], err: BIOExit.Failure[E] => MiniBIO[E1, B], succ: A => MiniBIO[E1, B]) extends MiniBIO[E1, B]

  implicit val BIOMiniBIO: BIO[MiniBIO] with BlockingIO[MiniBIO] = new BIO[MiniBIO] with BlockingIO[MiniBIO] {
    override def pure[A](a: A): MiniBIO[Nothing, A] = sync(a)
    override def flatMap[E, A, E1 >: E, B](r: MiniBIO[E, A])(f: A => MiniBIO[E1, B]): MiniBIO[E1, B] = FlatMap(r, f)
    override def fail[E](v: => E): MiniBIO[E, Nothing] = Fail(() => BIOExit.Error(v, Trace.empty))
    override def terminate(v: => Throwable): MiniBIO[Nothing, Nothing] = Fail.terminate(v)

    override def syncThrowable[A](effect: => A): MiniBIO[Throwable, A] = Sync {
      () => try { BIOExit.Success(effect) } catch { case e: Throwable => BIOExit.Error(e, Trace.empty) }
    }
    override def sync[A](effect: => A): MiniBIO[Nothing, A] = Sync(() => BIOExit.Success(effect))

    override def redeem[E, A, E2, B](r: MiniBIO[E, A])(err: E => MiniBIO[E2, B], succ: A => MiniBIO[E2, B]): MiniBIO[E2, B] = {
      Redeem[E, A, E2, B](r, {
        case BIOExit.Termination(t, e, c) => Fail.halt(BIOExit.Termination(t, e, c))
        case BIOExit.Error(e, _) => err(e)
      }, succ)
    }

    override def catchAll[E, A, E2, A2 >: A](r: MiniBIO[E, A])(f: E => MiniBIO[E2, A2]): MiniBIO[E2, A2] = redeem(r)(f, pure)

    override def catchSome[E, A, E2 >: E, A2 >: A](r: MiniBIO[E, A])(f: PartialFunction[E, MiniBIO[E2, A2]]): MiniBIO[E2, A2] = {
      Redeem[E, A, E2, A2](r, {
        case BIOExit.Termination(t, e, c) => Fail.halt(BIOExit.Termination(t, e, c))
        case exit@BIOExit.Error(e, _) => f.applyOrElse(e, (_: E) => Fail.halt(exit))
      }, pure)
    }

    override def bracketCase[E, A, B](acquire: MiniBIO[E, A])(release: (A, BIOExit[E, B]) => MiniBIO[Nothing, Unit])(use: A => MiniBIO[E, B]): MiniBIO[E, B] = {
      // does not propagate error in release in case `use` fails, propagates only error from `use`
      flatMap(acquire)(a =>
        Redeem[E, B, E, B](
          io = use(a)
        , err = e => Redeem[Nothing, Unit, E, Nothing](release(a, e), err = _ => Fail(() => e), succ = _ => Fail(() => e))
        , succ = v => map(release(a, BIOExit.Success(v)))(_ => v)
        )
      )
    }

    override def sandbox[E, A](r: MiniBIO[E, A]): MiniBIO[BIOExit.Failure[E], A] = {
      Redeem[E, A, BIOExit.Failure[E], A](r, e => fail(e), pure)
    }

    override def traverse[E, A, B](l: Iterable[A])(f: A => MiniBIO[E, B]): MiniBIO[E, List[B]] = {
      val x = l.foldLeft(pure(Nil): MiniBIO[E, List[B]]) { (acc, a) =>
        flatMap(acc)(list => map(f(a))(_ :: list))
      }
      map(x)(_.reverse)
    }

    override def shiftBlocking[R, E, A](f: MiniBIO[E, A]): MiniBIO[E, A] = f

    override def syncBlocking[A](f: => A): MiniBIO[Throwable, A] = sync(f)

    override def syncInterruptibleBlocking[A](f: => A): MiniBIO[Throwable, A] = sync(f)
  }
}
