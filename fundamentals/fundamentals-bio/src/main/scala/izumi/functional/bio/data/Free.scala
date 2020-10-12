package izumi.functional.bio.data

import izumi.functional.bio.BIOMonad

sealed trait Free[S[_, _], +E, +A] {
  @inline final def flatMap[B, E1 >: E](fun: A => Free[S, E1, B]): Free[S, E1, B] = Free.FlatMapped[S, E, E1, A, B](this, fun)
  @inline final def map[B](fun: A => B): Free[S, E, B] = flatMap(a => Free.pure[S, B](fun(a)))
  @inline final def as[B](as: B): Free[S, E, B] = map(_ => as)
  @inline final def *>[B, E1 >: E](sc: Free[S, E1, B]): Free[S, E1, B] = flatMap(_ => sc)
  @inline final def <*[B, E1 >: E](sc: Free[S, E1, B]): Free[S, E1, A] = flatMap(r => sc.as(r))

  @inline final def void: Free[S, E, Unit] = map(_ => ())

  @inline final def mapK[T[_, _]](f: S ~>> T): Free[T, E, A] = {
    foldMap[Free[T, +?, +?]] {
      new FunctionKK[S, Free[T, ?, ?]] { def apply[E1, A1](sb: S[E1, A1]): Free[T, E1, A1] = Free.Suspend(f(sb)) }
    }
  }

  @inline def foldMap[G[+_, +_]](transform: S ~>> G)(implicit M: BIOMonad[G]): G[E, A] = {
    this match {
      case Free.Pure(a) => M.pure(a)
      case Free.Suspend(a) => transform(a)
      case Free.FlatMapped(sub, cont) =>
        sub match {
          case Free.FlatMapped(sub2, cont2) => sub2.flatMap(a => cont2(a).flatMap(cont)).foldMap(transform)
          case another => another.foldMap(transform).flatMap(cont(_).foldMap(transform))
        }
    }
  }
}

object Free {
  @inline def pure[S[_, _], A](a: A): Free[S, Nothing, A] = Pure(a)
  @inline def lift[S[_, _], E, A](s: S[E, A]): Free[S, E, A] = Suspend(s)

  private final case class Pure[S[_, _], A](a: A) extends Free[S, Nothing, A] {
    override def toString: String = s"Pure:[$a]"
  }
  private final case class Suspend[S[_, _], E, A](a: S[E, A]) extends Free[S, E, A] {
    override def toString: String = s"Suspend:[$a]"
  }
  private final case class FlatMapped[S[_, _], E, E1 >: E, A, B](sub: Free[S, E, A], cont: A => Free[S, E1, B]) extends Free[S, E1, B] {
    override def toString: String = s"FlatMap:[sub=$sub]"
  }

  @inline implicit def BIOMonadInstances[S[_, _]]: BIOMonad[Free[S, +?, +?]] = new BIOMonadInstances[S]

  final class BIOMonadInstances[S[_, _]] extends BIOMonad[Free[S, +?, +?]] {
    @inline override def flatMap[R, E, A, B](r: Free[S, E, A])(f: A => Free[S, E, B]): Free[S, E, B] = r.flatMap(f)
    @inline override def pure[A](a: A): Free[S, Nothing, A] = Free.pure(a)
  }
}
