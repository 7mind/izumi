package izumi.functional.bio.data

import izumi.functional.bio.Monad2

import scala.annotation.nowarn

sealed abstract class Free[+S[_, _], +E, +A] {
  @inline final def flatMap[S1[e, a] >: S[e, a], B, E1 >: E](fun: A => Free[S1, E1, B]): Free[S1, E1, B] = Free.FlatMapped[S1, E, E1, A, B](this, fun)
  @inline final def map[B](fun: A => B): Free[S, E, B] = flatMap(a => Free.pure[S, B](fun(a)))
  @inline final def as[B](as: => B): Free[S, E, B] = map(_ => as)
  @inline final def *>[S1[e, a] >: S[e, a], B, E1 >: E](sc: Free[S1, E1, B]): Free[S1, E1, B] = flatMap(_ => sc)
  @inline final def <*[S1[e, a] >: S[e, a], B, E1 >: E](sc: Free[S1, E1, B]): Free[S1, E1, A] = flatMap(r => sc.as(r))

  @inline final def void: Free[S, E, Unit] = map(_ => ())

  @inline final def mapK[S1[e, a] >: S[e, a], T[_, _]](f: S1 ~>> T): Free[T, E, A] = {
    foldMap[S1, Free[T, +_, +_]](Morphism2(Free Suspend f(_)))
  }

  // FIXME: Scala 3.1.4 bug: false unexhaustive match warning
  @nowarn("msg=pattern case: Free.FlatMapped")
  @inline final def foldMap[S1[e, a] >: S[e, a], G[+_, +_]](transform: S1 ~>> G)(implicit G: Monad2[G]): G[E, A] = {
    this match {
      case Free.Pure(a) => G.pure(a)
      case Free.Suspend(a) => transform.apply(a)
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

  final case class Pure[S[_, _], A](a: A) extends Free[S, Nothing, A] {
    override def toString: String = s"Pure:[$a]"
  }
  final case class Suspend[S[_, _], E, A](a: S[E, A]) extends Free[S, E, A] {
    override def toString: String = s"Suspend:[$a]"
  }
  final case class FlatMapped[S[_, _], E, E1 >: E, A, B](sub: Free[S, E, A], cont: A => Free[S, E1, B]) extends Free[S, E1, B] {
    override def toString: String = s"FlatMapped:[sub=$sub]"
  }

  @inline implicit def FreeMonadInstances[S[_, _]]: Monad2[Free[S, +_, +_]] = new Monad2Instance[S]

  object Monad2Instance extends Monad2Instance[Nothing]
  class Monad2Instance[S[_, _]] extends Monad2[Free[S, +_, +_]] {
    @inline override def flatMap[E, A, B](r: Free[S, E, A])(f: A => Free[S, E, B]): Free[S, E, B] = r.flatMap(f)
    @inline override def pure[A](a: A): Free[S, Nothing, A] = Free.pure(a)
    @inline override def *>[E, A, B](f: Free[S, E, A], next: => Free[S, E, B]): Free[S, E, B] = f *> next
    @inline override def <*[E, A, B](f: Free[S, E, A], next: => Free[S, E, B]): Free[S, E, A] = f <* next
    @inline override def as[E, A, B](r: Free[S, E, A])(v: => B): Free[S, E, B] = r.as(v)
    @inline override def void[E, A](r: Free[S, E, A]): Free[S, E, Unit] = r.void
  }
}
