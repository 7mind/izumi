package izumi.functional.bio.data

import izumi.functional.bio.{BIOExit, BIOPanic}

import scala.language.existentials

sealed trait Free[S[_, _], +E, +A] {
  @inline final def flatMap[B, E1 >: E](fun: A => Free[S, E1, B]): Free[S, E1, B] = Free.FlatMap[S, E, E1, A, B](this, fun)
  @inline final def *>[B, E1 >: E](sc: Free[S, E1, B]): Free[S, E1, B] = flatMap(_ => sc)
  @inline final def map[B](fun: A => B): Free[S, E, B] = flatMap(a => Free.pure[S, B](fun(a)))

  @inline final def redeem[B, E1](err: E => Free[S, E1, B], succ: A => Free[S, E1, B]): Free[S, E1, B] = Free.Redeem(this, err, succ)
  @inline final def sandbox: Free[S, Nothing, BIOExit[E, A]] = Free.Sandbox(this)
  @inline final def catchAll[A1 >: A, E1](err: E => Free[S, E1, A1]): Free[S, E1, A1] = Free.Redeem(this, err, (a: A) => Free.pure(a))
  @inline final def guarantee(g: Free[S, Nothing, Unit]): Free[S, E, A] = Free.Guarantee[S, E, A](this, g)
  @inline final def bracket[B, E1 >: E](release: A => Free[S, Nothing, Unit])(use: A => Free[S, E1, B]): Free[S, E1, B] = Free.Bracket(this, release, use)

  @inline final def void: Free[S, E, Unit] = map(_ => ())

  @inline def foldMap[G[+_, +_]](transform: S ~>> G)(implicit G: BIOPanic[G]): G[E, A] = {
    this match {
      case Free.Pure(a) => G.pure(a)
      case Free.Suspend(a) => transform(a)
      case Free.Guarantee(sub, g) => sub.foldMap(transform).guarantee(g.foldMap(transform))
      case Free.Redeem(sub, err, suc) =>
        sub
          .foldMap(transform).redeem(
            err(_).foldMap(transform),
            suc(_).foldMap(transform),
          )
      case s: Free.Sandbox[S, _, _] =>
        s.sub.foldMap(transform).sandboxBIOExit.map(_.asInstanceOf[A])
      case Free.Bracket(acquire, release, use) =>
        acquire.foldMap(transform).bracket(a => release(a).foldMap(transform))(a => use(a).foldMap(transform))
      case Free.FlatMap(sub, cont) =>
        sub match {
          case Free.FlatMap(sub2, cont2) => sub2.flatMap(a => cont2(a).flatMap(cont)).foldMap(transform)
          case another => another.foldMap(transform).flatMap(cont(_).foldMap(transform))
        }
    }
  }
}

object Free {
  @inline def pure[S[_, _], A](a: A): Free[S, Nothing, A] = Pure(a)
  @inline def lift[S[_, _], E, A](s: S[E, A]): Free[S, E, A] = Suspend(s)
  @inline def bracket[F[+_, +_], S[_, _], E, A0, A](
    acquire: Free[S, E, A0]
  )(release: A0 => Free[S, Nothing, Unit]
  )(use: A0 => Free[S, E, A]
  ): Free[S, E, A] = {
    Bracket(acquire, release, use)
  }

  private[data] final case class Pure[S[_, _], A](a: A) extends Free[S, Nothing, A]
  private[data] final case class Suspend[S[_, _], E, A](a: S[E, A]) extends Free[S, E, A]
  private[data] final case class FlatMap[S[_, _], E, E1 >: E, A, B](sub: Free[S, E, A], cont: A => Free[S, E1, B]) extends Free[S, E1, B] {
    override def toString: String = s"${this.getClass.getName}[cont=${cont.getClass.getSimpleName}]"
  }
  private[data] final case class Guarantee[S[_, _], E, A](sub: Free[S, E, A], guarantee: Free[S, Nothing, Unit]) extends Free[S, E, A]
  private[data] final case class Sandbox[S[_, _], E, A](sub: Free[S, E, A]) extends Free[S, Nothing, BIOExit[E, A]]
  private[data] final case class Redeem[S[_, _], E, E1, A, B](sub: Free[S, E, A], err: E => Free[S, E1, B], suc: A => Free[S, E1, B]) extends Free[S, E1, B]

  private[data] final case class Bracket[S[_, _], E, A0, A](
    acquire: Free[S, E, A0],
    release: A0 => Free[S, Nothing, Unit],
    use: A0 => Free[S, E, A],
  ) extends Free[S, E, A] {
    override def toString: String = s"${this.getClass.getName}[bracket=${use.getClass.getSimpleName}]"
  }
}
