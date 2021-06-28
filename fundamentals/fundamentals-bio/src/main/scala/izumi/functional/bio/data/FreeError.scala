package izumi.functional.bio.data

import izumi.functional.bio.Error2
import izumi.functional.bio.data.FreeError.fail

import scala.util.Try

sealed abstract class FreeError[+S[_, _], +E, +A] {
  @inline final def flatMap[S1[e, a] >: S[e, a], B, E1 >: E](fun: A => FreeError[S1, E1, B]): FreeError[S1, E1, B] = FreeError.FlatMapped[S1, E, E1, A, B](this, fun)
  @inline final def map[B](fun: A => B): FreeError[S, E, B] = flatMap(a => FreeError.pure[S, B](fun(a)))
  @inline final def as[B](as: => B): FreeError[S, E, B] = map(_ => as)
  @inline final def *>[S1[e, a] >: S[e, a], B, E1 >: E](sc: FreeError[S1, E1, B]): FreeError[S1, E1, B] = flatMap(_ => sc)
  @inline final def <*[S1[e, a] >: S[e, a], B, E1 >: E](sc: FreeError[S1, E1, B]): FreeError[S1, E1, A] = flatMap(r => sc.as(r))

  @inline final def redeem[S1[e, a] >: S[e, a], B, E1](err: E => FreeError[S1, E1, B], succ: A => FreeError[S1, E1, B]): FreeError[S1, E1, B] =
    FreeError.Redeem(this, err, succ)
  @inline final def redeemPure[B](err: E => B, succ: A => B): FreeError[S, Nothing, B] =
    FreeError.Redeem[S, E, Nothing, A, B](this, e => FreeError.pure(err(e)), a => FreeError.pure(succ(a)))
  @inline final def catchAll[S1[e, a] >: S[e, a], A1 >: A, E1](err: E => FreeError[S1, E1, A1]): FreeError[S1, E1, A1] =
    FreeError.Redeem[S1, E, E1, A, A1](this, err, FreeError.pure)
  @inline final def catchSome[S1[e, a] >: S[e, a], A2 >: A, E1 >: E](err: PartialFunction[E, FreeError[S1, E1, A2]]): FreeError[S1, E1, A2] =
    FreeError.Redeem[S1, E, E1, A, A2](this, err.orElse { case e => FreeError.fail(e) }, FreeError.pure)
  @inline final def flip: FreeError[S, A, E] =
    FreeError.Redeem[S, E, A, A, E](this, FreeError.pure, FreeError.fail(_))

  @inline final def guarantee[S1[e, a] >: S[e, a]](g: FreeError[S1, Nothing, Unit]): FreeError[S1, E, A] =
    FreeError.Redeem[S1, E, E, A, A](this, e => g *> fail(e), g.as(_))

  @inline final def void: FreeError[S, E, Unit] = map(_ => ())

  @inline final def mapK[S1[e, a] >: S[e, a], T[_, _]](f: S1 ~>> T): FreeError[T, E, A] = {
    foldMap[S1, FreeError[T, +_, +_]](Morphism2(FreeError lift f(_)))
  }

  @inline final def foldMap[S1[e, a] >: S[e, a], G[+_, +_]](transform: S1 ~>> G)(implicit G: Error2[G]): G[E, A] = {
    this match {
      case FreeError.Pure(a) => G.pure(a)
      case FreeError.Suspend(a) => transform(a)
      case FreeError.Fail(fail) => G.fail(fail())
      case FreeError.Redeem(sub, err, suc) =>
        sub
          .foldMap(transform).redeem(
            err(_).foldMap(transform),
            suc(_).foldMap(transform),
          )
      case FreeError.FlatMapped(sub, cont) =>
        sub match {
          case FreeError.FlatMapped(sub2, cont2) => sub2.flatMap(a => cont2(a).flatMap(cont)).foldMap(transform)
          case another => another.foldMap(transform).flatMap(cont(_).foldMap(transform))
        }
    }
  }
}

object FreeError {
  @inline def unit[S[_, _]]: FreeError[S, Nothing, Unit] = Pure(())
  @inline def pure[S[_, _], A](a: A): FreeError[S, Nothing, A] = Pure(a)
  @inline def lift[S[_, _], E, A](s: S[E, A]): FreeError[S, E, A] = Suspend(s)
  @inline def fail[S[_, _], E](e: => E): FreeError[S, E, Nothing] = Fail(() => e)

  final case class Pure[S[_, _], A](a: A) extends FreeError[S, Nothing, A] {
    override def toString: String = s"Pure:[$a]"
  }
  final case class Suspend[S[_, _], E, A](s: S[E, A]) extends FreeError[S, E, A] {
    override def toString: String = s"Suspend:[$s]"
  }
  final case class Fail[S[_, _], E](fail: () => E) extends FreeError[S, E, Nothing] {
    override def toString: String = s"Fail:[$fail]"
  }
  final case class FlatMapped[S[_, _], E, E1 >: E, A, B](sub: FreeError[S, E, A], cont: A => FreeError[S, E1, B]) extends FreeError[S, E1, B] {
    override def toString: String = s"FlatMapped:[sub=$sub]"
  }
  final case class Redeem[S[_, _], E, E1, A, B](sub: FreeError[S, E, A], err: E => FreeError[S, E1, B], suc: A => FreeError[S, E1, B]) extends FreeError[S, E1, B] {
    override def toString: String = s"Redeem:[sub=$sub]"
  }

  @inline implicit def FreeErrorInstances[S[_, _]]: Error2[FreeError[S, +_, +_]] = Error2Instance.asInstanceOf[Error2Instance[S]]

  object Error2Instance extends Error2Instance[Any]
  class Error2Instance[S[_, _]] extends Error2[FreeError[S, +_, +_]] {
    @inline override def flatMap[R, E, A, B](r: FreeError[S, E, A])(f: A => FreeError[S, E, B]): FreeError[S, E, B] = r.flatMap(f)
    @inline override def *>[R, E, A, B](f: FreeError[S, E, A], next: => FreeError[S, E, B]): FreeError[S, E, B] = f *> next
    @inline override def <*[R, E, A, B](f: FreeError[S, E, A], next: => FreeError[S, E, B]): FreeError[S, E, A] = f <* next
    @inline override def as[R, E, A, B](r: FreeError[S, E, A])(v: => B): FreeError[S, E, B] = r.as(v)
    @inline override def void[R, E, A](r: FreeError[S, E, A]): FreeError[S, E, Unit] = r.void
    @inline override def catchAll[R, E, A, E2](r: FreeError[S, E, A])(f: E => FreeError[S, E2, A]): FreeError[S, E2, A] = r.catchAll(f)
    @inline override def catchSome[R, E, A, E1 >: E](r: FreeError[S, E, A])(f: PartialFunction[E, FreeError[S, E1, A]]): FreeError[S, E1, A] = r.catchSome(f)

    @inline override def pure[A](a: A): FreeError[S, Nothing, A] = FreeError.pure(a)
    @inline override def fail[E](v: => E): FreeError[S, E, Nothing] = FreeError.fail(v)
    @inline override def guarantee[R, E, A](f: FreeError[S, E, A], cleanup: FreeError[S, Nothing, Unit]): FreeError[S, E, A] = {
      f.redeem(e => cleanup *> fail(e), cleanup.as(_))
    }

    @inline override def fromEither[E, V](effect: => Either[E, V]): FreeError[S, E, V] = FreeError.unit *> {
      effect match {
        case Left(value) => fail(value)
        case Right(value) => pure(value)
      }
    }

    @inline override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): FreeError[S, E, A] = FreeError.unit *> {
      effect match {
        case None => fail(errorOnNone)
        case Some(value) => pure(value)
      }
    }

    @inline override def fromTry[A](effect: => Try[A]): FreeError[S, Throwable, A] = fromEither(effect.toEither)
  }
}
