package izumi.functional.bio.data

import izumi.functional.bio.{Exit, Panic2}

import scala.annotation.nowarn
import scala.util.Try

sealed abstract class FreePanic[+S[_, _], +E, +A] {
  @inline final def flatMap[S1[e, a] >: S[e, a], B, E1 >: E](fun: A => FreePanic[S1, E1, B]): FreePanic[S1, E1, B] = FreePanic.FlatMapped[S1, E, E1, A, B](this, fun)
  @inline final def map[B](fun: A => B): FreePanic[S, E, B] = flatMap(a => FreePanic.pure[S, B](fun(a)))
  @inline final def as[B](as: => B): FreePanic[S, E, B] = map(_ => as)
  @inline final def *>[S1[e, a] >: S[e, a], B, E1 >: E](sc: FreePanic[S1, E1, B]): FreePanic[S1, E1, B] = flatMap(_ => sc)
  @inline final def <*[S1[e, a] >: S[e, a], B, E1 >: E](sc: FreePanic[S1, E1, B]): FreePanic[S1, E1, A] = flatMap(r => sc.as(r))

  @inline final def sandbox: FreePanic[S, Exit.Failure[E], A] =
    FreePanic.Sandbox(this)

  @inline final def redeem[S1[e, a] >: S[e, a], B, E1](err: E => FreePanic[S1, E1, B], succ: A => FreePanic[S1, E1, B]): FreePanic[S1, E1, B] =
    FreePanic.Redeem(this, err, succ)
  @inline final def redeemPure[B](err: E => B, succ: A => B): FreePanic[S, Nothing, B] =
    FreePanic.Redeem[S, E, Nothing, A, B](this, e => FreePanic.pure(err(e)), a => FreePanic.pure(succ(a)))
  @inline final def catchAll[S1[e, a] >: S[e, a], A1 >: A, E1](err: E => FreePanic[S1, E1, A1]): FreePanic[S1, E1, A1] =
    FreePanic.Redeem[S1, E, E1, A, A1](this, err, FreePanic.pure)
  @inline final def catchSome[S1[e, a] >: S[e, a], A2 >: A, E1 >: E](err: PartialFunction[E, FreePanic[S1, E1, A2]]): FreePanic[S1, E1, A2] =
    FreePanic.Redeem[S1, E, E1, A, A2](this, err.orElse { case e => FreePanic.fail(e) }, FreePanic.pure)
  @inline final def flip: FreePanic[S, A, E] =
    FreePanic.Redeem[S, E, A, A, E](this, FreePanic.pure, FreePanic.fail(_))

  @inline final def bracket[S1[e, a] >: S[e, a], B, E1 >: E](release: A => FreePanic[S1, Nothing, Unit])(use: A => FreePanic[S1, E1, B]): FreePanic[S1, E1, B] =
    FreePanic.BracketCase(this, (a: A, _: Exit[E1, B]) => release(a), use)
  @inline final def bracketCase[S1[e, a] >: S[e, a], B, E1 >: E](
    release: (A, Exit[E1, B]) => FreePanic[S1, Nothing, Unit]
  )(use: A => FreePanic[S1, E1, B]
  ): FreePanic[S1, E1, B] =
    FreePanic.BracketCase(this, release, use)
  @inline final def guarantee[S1[e, a] >: S[e, a]](g: FreePanic[S1, Nothing, Unit]): FreePanic[S1, E, A] =
    FreePanic.BracketCase[S1, E, A, A](this, (_: A, _: Exit[E, A]) => g, FreePanic.pure)

  @inline final def void: FreePanic[S, E, Unit] = map(_ => ())

  @inline final def mapK[S1[e, a] >: S[e, a], T[_, _]](f: S1 ~>> T): FreePanic[T, E, A] = {
    foldMap[S1, FreePanic[T, +_, +_]](Morphism2(FreePanic lift f(_)))
  }

  // FIXME: Scala 3.1.4 bug: false unexhaustive match warning
  @nowarn("msg=(pattern case: FreePanic.FlatMapped)|(Unreachable case)")
  @inline final def foldMap[S1[e, a] >: S[e, a], G[+_, +_]](transform: S1 ~>> G)(implicit G: Panic2[G]): G[E, A] = {
    this match {
      case FreePanic.Pure(a) => G.pure(a)
      case FreePanic.Suspend(a) => transform(a)
      case FreePanic.Fail(fail) => G.fail(fail())
      case FreePanic.Terminate(termination) => G.terminate(termination())
      case s: FreePanic.SelfInterrupt[S] @unchecked =>
        @inline def selfInterrupt[e, a](s: FreePanic[S, e, a] & FreePanic.SelfInterrupt[S]): G[e, a] = {
          (s: FreePanic[S, e, a] @unchecked) match {
            case FreePanic.SelfInterrupt() => G.sendInterruptToSelf
          }
        }
        selfInterrupt(s)
      case FreePanic.Redeem(sub, err, suc) =>
        sub
          .foldMap(transform).redeem(
            err(_).foldMap(transform),
            suc(_).foldMap(transform),
          )
      case s: FreePanic.Sandbox[S, E, A] @unchecked =>
        @inline def foldMapSandbox[e, a](s: FreePanic[S, e, a] & FreePanic.Sandbox[S, e, a]): G[e, a] = {
          (s: FreePanic[S, e, a] @unchecked) match {
            case FreePanic.Sandbox(sub) => sub.foldMap(transform).sandbox
          }
        }
        foldMapSandbox[E, A](s)
      case FreePanic.Uninterruptible(sub) =>
        G.uninterruptible(sub.foldMap(transform))
      case s: FreePanic.BracketCase[S, E, ?, A] @unchecked =>
        s.acquire.foldMap[S1, G](transform).bracketCase[E, A]((a, e: Exit[E, A]) => s.release(a, e).foldMap[S1, G](transform))(a => s.use(a).foldMap[S1, G](transform))
      case FreePanic.UninterruptibleExcept(sub) =>
        G.uninterruptibleExcept((r: RestoreInterruption2[G]) => sub(FreePanic.CapturedRestoreInterruption.captureRestore(r)).foldMap(transform))
      case s: FreePanic.BracketExcept[S, E, ?, A] @unchecked =>
        G.bracketExcept(r => s.acquire(FreePanic.CapturedRestoreInterruption.captureRestore(r)).foldMap(transform))(
          (a, e: Exit[E, A]) => s.release(a, e).foldMap(transform)
        )(a => s.use(a).foldMap(transform))
      case s: FreePanic.CapturedRestoreInterruption[S, G, E, A] @unchecked =>
        @inline def applyRestore[e, a](s: FreePanic[S, e, a] & FreePanic.CapturedRestoreInterruption[S, G, e, a]) = {
          s match {
            case FreePanic.CapturedRestoreInterruption(restoreInterruption2, sub) =>
              restoreInterruption2(sub.foldMap(transform))
          }
        }
        applyRestore(s)
      case FreePanic.FlatMapped(sub, cont) =>
        sub match {
          case FreePanic.FlatMapped(sub2, cont2) => sub2.flatMap(a => cont2(a).flatMap(cont)).foldMap(transform)
          case another => another.foldMap(transform).flatMap(cont(_).foldMap(transform))
        }
    }
  }
}

object FreePanic {
  @inline def unit[S[_, _]]: FreePanic[S, Nothing, Unit] = Pure(())
  @inline def pure[S[_, _], A](a: A): FreePanic[S, Nothing, A] = Pure(a)
  @inline def lift[S[_, _], E, A](s: S[E, A]): FreePanic[S, E, A] = Suspend(s)
  @inline def fail[S[_, _], E](e: => E): FreePanic[S, E, Nothing] = Fail(() => e)
  @inline def terminate[S[_, _]](e: => Throwable): FreePanic[S, Nothing, Nothing] = Terminate(() => e)
  @inline def sendInterruptToSelf[S[_, _]]: FreePanic[S, Nothing, Unit] = SelfInterrupt()

  @inline def uninterruptible[S[_, _], E, A](s: FreePanic[S, E, A]): FreePanic[S, E, A] = Uninterruptible(s)
  @inline def uninterruptibleExcept[S[_, _], E, A](s: RestoreInterruption2[FreePanic[S, +_, +_]] => FreePanic[S, E, A]): FreePanic[S, E, A] = UninterruptibleExcept(s)

  @inline def bracket[S[_, _], E, A0, A](
    acquire: FreePanic[S, E, A0]
  )(release: A0 => FreePanic[S, Nothing, Unit]
  )(use: A0 => FreePanic[S, E, A]
  ): FreePanic[S, E, A] = {
    BracketCase(acquire, (a: A0, _: Exit[E, A]) => release(a), use)
  }

  @inline def bracketCase[S[_, _], E, A0, A](
    acquire: FreePanic[S, E, A0]
  )(release: (A0, Exit[E, A]) => FreePanic[S, Nothing, Unit]
  )(use: A0 => FreePanic[S, E, A]
  ): FreePanic[S, E, A] = {
    BracketCase(acquire, release, use)
  }

  @inline def bracketExcept[S[_, _], E, A0, A](
    acquire: RestoreInterruption2[FreePanic[S, +_, +_]] => FreePanic[S, E, A0]
  )(release: A0 => FreePanic[S, Nothing, Unit]
  )(use: A0 => FreePanic[S, E, A]
  ): FreePanic[S, E, A] = {
    BracketExcept(acquire, (a: A0, _: Exit[E, A]) => release(a), use)
  }

  final case class Pure[S[_, _], A](a: A) extends FreePanic[S, Nothing, A] {
    override def toString: String = s"Pure:[$a]"
  }
  final case class Suspend[S[_, _], E, A](s: S[E, A]) extends FreePanic[S, E, A] {
    override def toString: String = s"Suspend:[$s]"
  }
  final case class Fail[S[_, _], E](fail: () => E) extends FreePanic[S, E, Nothing] {
    override def toString: String = s"Fail:[$fail]"
  }
  final case class Terminate[S[_, _]](termination: () => Throwable) extends FreePanic[S, Nothing, Nothing] {
    override def toString: String = s"Terminate:[$termination]"
  }
  final case class SelfInterrupt[S[_, _]]() extends FreePanic[S, Nothing, Unit] {
    override def toString: String = "SelfInterrupt"
  }
  final case class CapturedRestoreInterruption[S[_, _], G[+_, +_], E, A](restoreInterruption2: RestoreInterruption2[G], sub: FreePanic[S, E, A])
    extends FreePanic[S, E, A] {
    override def toString: String = s"CapturedRestoreInterruption:[capturedRestore=$restoreInterruption2,sub=$sub]"
  }
  object CapturedRestoreInterruption {
    // taken from doobie library
    def captureRestore[S[_, _], G[+_, +_]](restoreG: RestoreInterruption2[G]): RestoreInterruption2[FreePanic[S, +_, +_]] = {
      Morphism2(f => CapturedRestoreInterruption(restoreG, f))
    }
  }
  final case class FlatMapped[S[_, _], E, E1 >: E, A, B](sub: FreePanic[S, E, A], cont: A => FreePanic[S, E1, B]) extends FreePanic[S, E1, B] {
    override def toString: String = s"FlatMapped:[sub=$sub]"
  }
  final case class Sandbox[S[_, _], E, A](sub: FreePanic[S, E, A]) extends FreePanic[S, Exit.Failure[E], A] {
    override def toString: String = s"Sandbox:[sub=$sub]"
  }
  final case class Redeem[S[_, _], E, E1, A, B](sub: FreePanic[S, E, A], err: E => FreePanic[S, E1, B], suc: A => FreePanic[S, E1, B]) extends FreePanic[S, E1, B] {
    override def toString: String = s"Redeem:[sub=$sub]"
  }
  final case class Uninterruptible[S[_, _], E, A](sub: FreePanic[S, E, A]) extends FreePanic[S, E, A] {
    override def toString: String = s"Uninterruptible:[sub=$sub]"
  }
  final case class UninterruptibleExcept[S[_, _], E, A](sub: RestoreInterruption2[FreePanic[S, +_, +_]] => FreePanic[S, E, A]) extends FreePanic[S, E, A] {
    override def toString: String = s"UninterruptibleExcept:[sub=${sub.getClass.getSimpleName}]"
  }
  final case class BracketCase[S[_, _], E, A0, A](
    acquire: FreePanic[S, E, A0],
    release: (A0, Exit[E, A]) => FreePanic[S, Nothing, Unit],
    use: A0 => FreePanic[S, E, A],
  ) extends FreePanic[S, E, A] {
    override def toString: String = s"BracketCase:[acquire=$acquire;use=${use.getClass.getSimpleName};release=${release.getClass.getSimpleName}]"
  }
  final case class BracketExcept[S[_, _], E, A0, A](
    acquire: RestoreInterruption2[FreePanic[S, +_, +_]] => FreePanic[S, E, A0],
    release: (A0, Exit[E, A]) => FreePanic[S, Nothing, Unit],
    use: A0 => FreePanic[S, E, A],
  ) extends FreePanic[S, E, A] {
    override def toString: String = s"BracketExcept:[acquire=$acquire;use=${use.getClass.getSimpleName};release=${release.getClass.getSimpleName}]"
  }

  @inline implicit def FreePanicInstances[S[_, _]]: Panic2[FreePanic[S, +_, +_]] = Panic2Instance.asInstanceOf[Panic2Instance[S]]

  object Panic2Instance extends Panic2Instance[Nothing]
  class Panic2Instance[S[_, _]] extends Panic2[FreePanic[S, +_, +_]] {
    @inline override final def flatMap[E, A, B](r: FreePanic[S, E, A])(f: A => FreePanic[S, E, B]): FreePanic[S, E, B] = r.flatMap(f)
    @inline override final def *>[E, A, B](f: FreePanic[S, E, A], next: => FreePanic[S, E, B]): FreePanic[S, E, B] = f *> next
    @inline override final def <*[E, A, B](f: FreePanic[S, E, A], next: => FreePanic[S, E, B]): FreePanic[S, E, A] = f <* next
    @inline override final def as[E, A, B](r: FreePanic[S, E, A])(v: => B): FreePanic[S, E, B] = r.as(v)
    @inline override final def void[E, A](r: FreePanic[S, E, A]): FreePanic[S, E, Unit] = r.void
    @inline override final def sandbox[E, A](r: FreePanic[S, E, A]): FreePanic[S, Exit.Failure[E], A] = r.sandbox
    @inline override final def catchAll[E, A, E2](r: FreePanic[S, E, A])(f: E => FreePanic[S, E2, A]): FreePanic[S, E2, A] = r.catchAll(f)
    @inline override final def catchSome[E, A, E1 >: E](r: FreePanic[S, E, A])(f: PartialFunction[E, FreePanic[S, E1, A]]): FreePanic[S, E1, A] = r.catchSome(f)
    @inline override final def bracketCase[E, A, B](
      acquire: FreePanic[S, E, A]
    )(release: (A, Exit[E, B]) => FreePanic[S, Nothing, Unit]
    )(use: A => FreePanic[S, E, B]
    ): FreePanic[S, E, B] = acquire.bracketCase(release)(use)
    @inline override final def guarantee[E, A](f: FreePanic[S, E, A], cleanup: FreePanic[S, Nothing, Unit]): FreePanic[S, E, A] = f.guarantee(cleanup)
    @inline override final def uninterruptible[E, A](r: FreePanic[S, E, A]): FreePanic[S, E, A] = FreePanic.Uninterruptible(r)
    @inline override final def uninterruptibleExcept[E, A](r: RestoreInterruption2[FreePanic[S, +_, +_]] => FreePanic[S, E, A]): FreePanic[S, E, A] =
      FreePanic.UninterruptibleExcept(r)
    @inline override final def bracketExcept[E, A, B](
      acquire: RestoreInterruption2[FreePanic[S, +_, +_]] => FreePanic[S, E, A]
    )(release: (A, Exit[E, B]) => FreePanic[S, Nothing, Unit]
    )(use: A => FreePanic[S, E, B]
    ): FreePanic[S, E, B] = FreePanic.BracketExcept(acquire, release, use)

    @inline override final def pure[A](a: A): FreePanic[S, Nothing, A] = FreePanic.pure(a)

    @inline override final def fail[E](v: => E): FreePanic[S, E, Nothing] = FreePanic.fail(v)
    @inline override final def terminate(v: => Throwable): FreePanic[S, Nothing, Nothing] = FreePanic.terminate(v)
    @inline override final def sendInterruptToSelf: FreePanic[S, Nothing, Unit] = FreePanic.sendInterruptToSelf

    @inline override final def fromEither[E, V](effect: => Either[E, V]): FreePanic[S, E, V] = FreePanic.unit *> {
      effect match {
        case Left(value) => fail(value)
        case Right(value) => pure(value)
      }
    }

    @inline override final def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): FreePanic[S, E, A] = FreePanic.unit *> {
      effect match {
        case None => fail(errorOnNone)
        case Some(value) => pure(value)
      }
    }

    @inline override final def fromTry[A](effect: => Try[A]): FreePanic[S, Throwable, A] = fromEither(effect.toEither)
  }
}
