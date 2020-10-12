package izumi.functional.bio.data

import izumi.functional.bio.{BIOExit, BIOPanic}

import scala.util.Try

sealed trait FreePanic[S[_, _], +E, +A] {
  @inline final def flatMap[B, E1 >: E](fun: A => FreePanic[S, E1, B]): FreePanic[S, E1, B] = FreePanic.FlatMapped[S, E, E1, A, B](this, fun)
  @inline final def map[B](fun: A => B): FreePanic[S, E, B] = flatMap(a => FreePanic.pure[S, B](fun(a)))
  @inline final def as[B](as: B): FreePanic[S, E, B] = map(_ => as)
  @inline final def *>[B, E1 >: E](sc: FreePanic[S, E1, B]): FreePanic[S, E1, B] = flatMap(_ => sc)
  @inline final def <*[B, E1 >: E](sc: FreePanic[S, E1, B]): FreePanic[S, E1, A] = flatMap(sc.as)

  @inline final def sandbox: FreePanic[S, BIOExit.Failure[E], A] =
    FreePanic.Sandbox(this)

  @inline final def redeem[B, E1](err: E => FreePanic[S, E1, B], succ: A => FreePanic[S, E1, B]): FreePanic[S, E1, B] =
    FreePanic.Redeem(this, err, succ)
  @inline final def redeemPure[B](err: E => B, succ: A => B): FreePanic[S, Nothing, B] =
    FreePanic.Redeem[S, E, Nothing, A, B](this, e => FreePanic.pure(err(e)), a => FreePanic.pure(succ(a)))
  @inline final def catchAll[A1 >: A, E1](err: E => FreePanic[S, E1, A1]): FreePanic[S, E1, A1] =
    FreePanic.Redeem(this, err, (a: A) => FreePanic.pure(a))
  @inline final def catchSome[A1 >: A, E1 >: E](err: PartialFunction[E, FreePanic[S, E1, A1]]): FreePanic[S, E1, A1] =
    FreePanic.Redeem(this, err.orElse[E, FreePanic[S, E1, A1]] { case e => FreePanic.fail(e) }, (a: A) => FreePanic.pure(a))
  @inline final def flip: FreePanic[S, A, E] =
    FreePanic.Redeem[S, E, A, A, E](this, FreePanic.pure, FreePanic.fail(_))

  @inline final def bracket[B, E1 >: E](release: A => FreePanic[S, Nothing, Unit])(use: A => FreePanic[S, E1, B]): FreePanic[S, E1, B] =
    FreePanic.BracketCase(this, (a: A, _: BIOExit[E1, B]) => release(a), use)
  @inline final def bracketCase[B, E1 >: E](release: (A, BIOExit[E1, B]) => FreePanic[S, Nothing, Unit])(use: A => FreePanic[S, E1, B]): FreePanic[S, E1, B] =
    FreePanic.BracketCase(this, release, use)
  @inline final def guarantee(g: FreePanic[S, Nothing, Unit]): FreePanic[S, E, A] =
    FreePanic.BracketCase[S, E, A, A](this, (_: A, _: BIOExit[E, A]) => g, FreePanic.pure)

  @inline final def void: FreePanic[S, E, Unit] = map(_ => ())

  @inline final def mapK[T[_, _]](f: S ~>> T): FreePanic[T, E, A] = {
    foldMap[FreePanic[T, +?, +?]] {
      new FunctionKK[S, FreePanic[T, ?, ?]] { def apply[E1, A1](sb: S[E1, A1]): FreePanic[T, E1, A1] = FreePanic.lift(f(sb)) }
    }
  }

  @inline def foldMap[G[+_, +_]](transform: S ~>> G)(implicit G: BIOPanic[G]): G[E, A] = {
    this match {
      case FreePanic.Pure(a) => G.pure(a)
      case FreePanic.Suspend(a) => transform(a)
      case FreePanic.Fail(fail) => G.fail(fail())
      case FreePanic.Terminate(termination) => G.terminate(termination())
      case FreePanic.Redeem(sub, err, suc) =>
        sub
          .foldMap(transform).redeem(
            err(_).foldMap(transform),
            suc(_).foldMap(transform),
          )
      case s: FreePanic.Sandbox[S, E, A] =>
        s.sub.foldMap(transform).sandbox.leftMap(_.asInstanceOf[E])
      case FreePanic.BracketCase(acquire, release, use) =>
        acquire.foldMap(transform).bracketCase((a, e: BIOExit[E, A]) => release(a, e).foldMap(transform))(a => use(a).foldMap(transform))
      case FreePanic.FlatMapped(sub, cont) =>
        sub match {
          case FreePanic.FlatMapped(sub2, cont2) => sub2.flatMap(a => cont2(a).flatMap(cont)).foldMap(transform)
          case another => another.foldMap(transform).flatMap(cont(_).foldMap(transform))
        }
    }
  }
}

object FreePanic {
  @inline def pure[S[_, _], A](a: A): FreePanic[S, Nothing, A] = Pure(a)
  @inline def lift[S[_, _], E, A](s: S[E, A]): FreePanic[S, E, A] = Suspend(s)
  @inline def fail[S[_, _], E](e: => E): FreePanic[S, E, Nothing] = Fail(() => e)
  @inline def terminate[S[_, _]](e: => Throwable): FreePanic[S, Nothing, Nothing] = Terminate(() => e)
  @inline def bracket[F[+_, +_], S[_, _], E, A0, A](
    acquire: FreePanic[S, E, A0]
  )(release: A0 => FreePanic[S, Nothing, Unit]
  )(use: A0 => FreePanic[S, E, A]
  ): FreePanic[S, E, A] = {
    BracketCase(acquire, (a: A0, _: BIOExit[E, A]) => release(a), use)
  }

  private final case class Pure[S[_, _], A](a: A) extends FreePanic[S, Nothing, A] {
    override def toString: String = s"Pure:[$a]"
  }
  private final case class Suspend[S[_, _], E, A](s: S[E, A]) extends FreePanic[S, E, A] {
    override def toString: String = s"Suspend:[$s]"
  }
  private final case class Fail[S[_, _], E](fail: () => E) extends FreePanic[S, E, Nothing] {
    override def toString: String = s"Fail:[$fail]"
  }
  private final case class Terminate[S[_, _]](termination: () => Throwable) extends FreePanic[S, Nothing, Nothing] {
    override def toString: String = s"Terminate:[$termination]"
  }
  private final case class FlatMapped[S[_, _], E, E1 >: E, A, B](sub: FreePanic[S, E, A], cont: A => FreePanic[S, E1, B]) extends FreePanic[S, E1, B] {
    override def toString: String = s"FlatMapped:[sub=$sub]"
  }
  private final case class Sandbox[S[_, _], E, A](sub: FreePanic[S, E, A]) extends FreePanic[S, BIOExit.Failure[E], A] {
    override def toString: String = s"Sandbox:[sub=$sub]"
  }
  private final case class Redeem[S[_, _], E, E1, A, B](sub: FreePanic[S, E, A], err: E => FreePanic[S, E1, B], suc: A => FreePanic[S, E1, B])
    extends FreePanic[S, E1, B] {
    override def toString: String = s"Redeem:[sub=$sub]"
  }

  private final case class BracketCase[S[_, _], E, A0, A](
    acquire: FreePanic[S, E, A0],
    release: (A0, BIOExit[E, A]) => FreePanic[S, Nothing, Unit],
    use: A0 => FreePanic[S, E, A],
  ) extends FreePanic[S, E, A] {
    override def toString: String = s"BracketCase:[acquire=$acquire;use=${use.getClass.getSimpleName}]"
  }

  @inline implicit def BIOPanicInstances[S[_, _]]: BIOPanic[FreePanic[S, +?, +?]] = new BIOPanicInstances[S]

  final class BIOPanicInstances[S[_, _]] extends BIOPanic[FreePanic[S, +?, +?]] {
    @inline override def flatMap[R, E, A, B](r: FreePanic[S, E, A])(f: A => FreePanic[S, E, B]): FreePanic[S, E, B] = r.flatMap(f)
    @inline override def sandbox[R, E, A](r: FreePanic[S, E, A]): FreePanic[S, BIOExit.Failure[E], A] = r.sandbox
    @inline override def catchAll[R, E, A, E2](r: FreePanic[S, E, A])(f: E => FreePanic[S, E2, A]): FreePanic[S, E2, A] = r.catchAll(f)
    @inline override def catchSome[R, E, A, E1 >: E](r: FreePanic[S, E, A])(f: PartialFunction[E, FreePanic[S, E1, A]]): FreePanic[S, E1, A] = r.catchSome(f)
    @inline override def bracketCase[R, E, A, B](
      acquire: FreePanic[S, E, A]
    )(release: (A, BIOExit[E, B]) => FreePanic[S, Nothing, Unit]
    )(use: A => FreePanic[S, E, B]
    ): FreePanic[S, E, B] = acquire.bracketCase(release)(use)

    @inline override def pure[A](a: A): FreePanic[S, Nothing, A] = FreePanic.pure(a)

    @inline override def fail[E](v: => E): FreePanic[S, E, Nothing] = FreePanic.fail(v)
    @inline override def terminate(v: => Throwable): FreePanic[S, Nothing, Nothing] = FreePanic.terminate(v)

    @inline override def fromEither[E, V](effect: => Either[E, V]): FreePanic[S, E, V] = FreePanic.pure(() => effect).flatMap {
      _() match {
        case Left(value) => fail(value)
        case Right(value) => pure(value)
      }
    }

    @inline override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): FreePanic[S, E, A] = FreePanic.pure(() => effect).flatMap {
      _() match {
        case None => fail(errorOnNone)
        case Some(value) => pure(value)
      }
    }

    @inline override def fromTry[A](effect: => Try[A]): FreePanic[S, Throwable, A] = fromEither(effect.toEither)
  }
}
