package izumi.functional.bio.data

import izumi.functional.bio.{BIOError, F}

sealed trait Free[F[+_, +_], S[_[+_, +_], _, _], +E, +A] {
  @inline final def flatMap[B, E1 >: E](fun: A => Free[F, S, E1, B]): Free[F, S, E1, B] = Free.FlatMap[F, S, E, E1, A, B](this, fun)
  @inline final def *>[B, E1 >: E](sc: Free[F, S, E1, B]): Free[F, S, E1, B] = flatMap(_ => sc)
  @inline final def map[B](fun: A => B)(implicit ap: FreeApplicative[S]): Free[F, S, E, B] = flatMap(a => Free.lift(ap.pure(fun(a))))

  @inline final def redeem[B, E1 >: E](err: E => Free[F, S, E1, B], succ: A => Free[F, S, E1, B]): Free[F, S, E1, B] = Free.Redeem(this, err, succ)
  @inline final def sandbox: Free[F, S, Nothing, Either[Throwable, Either[E, A]]] = Free.Sandbox(this)
  @inline final def catchAll[A1 >: A, E1 >: E](err: E => Free[F, S, E1, A1])(implicit ap: FreeApplicative[S]): Free[F, S, E1, A1] =
    Free.Redeem(this, err, (a: A) => Free.lift(ap.pure(a)))
  @inline final def guarantee(g: Free[F, S, Nothing, Unit]): Free[F, S, E, A] = Free.Guarantee[F, S, E, A](this, g)

  @inline final def void(implicit ap: FreeApplicative[S]): Free[F, S, E, Unit] = map(_ => ())

  /**
    * Execute free via interpreter. Tailrec works only on [[Free.FlatMap]] due to wrapped effect.
    * Anyway if you will use IO with trampoline optimization this function is stacksafe and will throw [[StackOverflowError]] only in case of using of [[cats.Id]].
    */
  @inline final def execute[Scope](
    scope: Scope,
    interpreter: FreeInterpreter[F, S, Scope],
  )(implicit
    M: BIOError[F]
  ): F[Nothing, FreeInterpreter.Result[F, S, Scope]] = {
    this match {
      case Free.Pure(a) =>
        interpreter.interpret(scope, a)
      case Free.Guarantee(sub, g) =>
        sub.execute(scope, interpreter).flatMap(r => g.execute(r.scope, interpreter).flatMap(_ => F.pure(r)))
      case Free.Redeem(sub, err, suc) =>
        sub.execute(scope, interpreter).flatMap {
          case FreeInterpreter.Result.Success(sc, v) => suc(v).execute(sc, interpreter)
          case FreeInterpreter.Result.Error(sc, e, _) => err(e.asInstanceOf[E]).execute(sc, interpreter)
          case f => F.pure(f)
        }
      case s: Free.Sandbox[F, S, _, _] =>
        s.sub.execute(scope, interpreter).map {
          case FreeInterpreter.Result.Success(sc, v) => FreeInterpreter.Result.Success(sc, Right(Right(v)))
          case FreeInterpreter.Result.Error(sc, e, _) => FreeInterpreter.Result.Success(sc, Right(Left(e)))
          case FreeInterpreter.Result.Termination(sc, e, _, _) => FreeInterpreter.Result.Success(sc, Left(e))
        }
      case Free.Bracket(acquire, release, use) =>
        acquire.flatMap(a => use(a).guarantee(release(a))).execute(scope, interpreter)
      case Free.FlatMap(sub, cont) =>
        sub match {
          case Free.FlatMap(sub2, cont2) => sub2.flatMap(a => cont2(a).flatMap(cont)).execute(scope, interpreter)
          case Free.Bracket(acquire, release, use) => acquire.flatMap(a => use(a).guarantee(release(a))).flatMap(cont).execute(scope, interpreter)
          case another =>
            another.execute(scope, interpreter).flatMap {
              case FreeInterpreter.Result.Success(sc, v) => cont(v).execute(sc, interpreter)
              case f => F.pure(f)
            }
        }
    }
  }
}

object Free {
  @inline def lift[F[+_, +_], S[_[+_, +_], _, _], E, A](s: S[F, E, A]): Free[F, S, E, A] = Pure(s)
  @inline def bracket[F[+_, +_], S[_[+_, +_], _, _], E, A0, A](
    acquire: Free[F, S, E, A0]
  )(release: A0 => Free[F, S, Nothing, Unit]
  )(use: A0 => Free[F, S, E, A]
  ): Free[F, S, E, A] = {
    Bracket(acquire, release, use)
  }

  private[data] final case class Pure[F[+_, +_], S[_[+_, +_], _, _], E, A](a: S[F, E, A]) extends Free[F, S, E, A]
  private[data] final case class FlatMap[F[+_, +_], S[_[+_, +_], _, _], E, E1 >: E, A, B](sub: Free[F, S, E, A], cont: A => Free[F, S, E1, B]) extends Free[F, S, E1, B] {
    override def toString: String = s"${this.getClass.getName}[cont=${cont.getClass.getSimpleName}]"
  }
  private[data] final case class Guarantee[F[+_, +_], S[_[+_, +_], _, _], E, A](sub: Free[F, S, E, A], guarantee: Free[F, S, Nothing, Unit]) extends Free[F, S, E, A]
  private[data] final case class Sandbox[F[+_, +_], S[_[+_, +_], _, _], E, A](sub: Free[F, S, E, A]) extends Free[F, S, Nothing, Either[Throwable, Either[E, A]]]
  private[data] final case class Redeem[F[+_, +_], S[_[+_, +_], _, _], E, E1 >: E, A, B](sub: Free[F, S, E, A], err: E => Free[F, S, E1, B], suc: A => Free[F, S, E1, B])
    extends Free[F, S, E1, B]

  private[data] final case class Bracket[F[+_, +_], S[_[+_, +_], _, _], E, A0, A](
    acquire: Free[F, S, E, A0],
    release: A0 => Free[F, S, Nothing, Unit],
    use: A0 => Free[F, S, E, A],
  ) extends Free[F, S, E, A] {
    override def toString: String = s"${this.getClass.getName}[bracket=${use.getClass.getSimpleName}]"
  }
}
