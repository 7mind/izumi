package izumi.functional.bio.data

import izumi.functional.bio.{BIOExit, BIOPanic}

sealed trait FreeBracket[S[_, _], +E, +A] {
  @inline final def flatMap[B, E1 >: E](fun: A => FreeBracket[S, E1, B]): FreeBracket[S, E1, B] = FreeBracket.FlatMap[S, E, E1, A, B](this, fun)
  @inline final def map[B](fun: A => B): FreeBracket[S, E, B] = flatMap(a => FreeBracket.pure[S, B](fun(a)))
  @inline final def as[B](as: B): FreeBracket[S, E, B] = map(_ => as)
  @inline final def *>[B, E1 >: E](sc: FreeBracket[S, E1, B]): FreeBracket[S, E1, B] = flatMap(_ => sc)
  @inline final def <*[B, E1 >: E](sc: FreeBracket[S, E1, B]): FreeBracket[S, E1, A] = flatMap(sc.as)

  @inline final def redeem[B, E1](err: E => FreeBracket[S, E1, B], succ: A => FreeBracket[S, E1, B]): FreeBracket[S, E1, B] = FreeBracket.Redeem(this, err, succ)
  @inline final def sandbox: FreeBracket[S, Nothing, BIOExit[E, A]] = FreeBracket.Sandbox(this)
  @inline final def catchAll[A1 >: A, E1](err: E => FreeBracket[S, E1, A1]): FreeBracket[S, E1, A1] = FreeBracket.Redeem(this, err, (a: A) => FreeBracket.pure(a))
  @inline final def guarantee(g: FreeBracket[S, Nothing, Unit]): FreeBracket[S, E, A] = FreeBracket.Guarantee[S, E, A](this, g)
  @inline final def bracket[B, E1 >: E](release: A => FreeBracket[S, Nothing, Unit])(use: A => FreeBracket[S, E1, B]): FreeBracket[S, E1, B] =
    FreeBracket.Bracket(this, release, use)

  @inline final def void: FreeBracket[S, E, Unit] = map(_ => ())

  @inline def foldMap[G[+_, +_]](transform: S ~>> G)(implicit G: BIOPanic[G]): G[E, A] = {
    this match {
      case FreeBracket.Pure(a) => G.pure(a)
      case FreeBracket.Suspend(a) => transform(a)
      case FreeBracket.Guarantee(sub, g) => sub.foldMap(transform).guarantee(g.foldMap(transform))
      case FreeBracket.Redeem(sub, err, suc) =>
        sub
          .foldMap(transform).redeem(
            err(_).foldMap(transform),
            suc(_).foldMap(transform),
          )
      case s: FreeBracket.Sandbox[S, _, _] =>
        s.sub.foldMap(transform).sandboxBIOExit.map(_.asInstanceOf[A])

      case FreeBracket.Bracket(acquire, release, use) =>
        acquire.foldMap(transform).bracket(a => release(a).foldMap(transform))(a => use(a).foldMap(transform))
      case FreeBracket.FlatMap(sub, cont) =>
        sub match {
          case FreeBracket.FlatMap(sub2, cont2) => sub2.flatMap(a => cont2(a).flatMap(cont)).foldMap(transform)
          case another => another.foldMap(transform).flatMap(cont(_).foldMap(transform))
        }

    }
  }
}

object FreeBracket {
  @inline def pure[S[_, _], A](a: A): FreeBracket[S, Nothing, A] = Pure(a)
  @inline def lift[S[_, _], E, A](s: S[E, A]): FreeBracket[S, E, A] = Suspend(s)
  @inline def bracket[F[+_, +_], S[_, _], E, A0, A](
    acquire: FreeBracket[S, E, A0]
  )(release: A0 => FreeBracket[S, Nothing, Unit]
  )(use: A0 => FreeBracket[S, E, A]
  ): FreeBracket[S, E, A] = {
    Bracket(acquire, release, use)
  }

  private[data] final case class Pure[S[_, _], A](a: A) extends FreeBracket[S, Nothing, A]
  private[data] final case class Suspend[S[_, _], E, A](a: S[E, A]) extends FreeBracket[S, E, A]
  private[data] final case class FlatMap[S[_, _], E, E1 >: E, A, B](sub: FreeBracket[S, E, A], cont: A => FreeBracket[S, E1, B]) extends FreeBracket[S, E1, B] {
    override def toString: String = s"${this.getClass.getName}[cont=${cont.getClass.getSimpleName}]"
  }
  private[data] final case class Guarantee[S[_, _], E, A](sub: FreeBracket[S, E, A], guarantee: FreeBracket[S, Nothing, Unit]) extends FreeBracket[S, E, A]
  private[data] final case class Sandbox[S[_, _], E, A](sub: FreeBracket[S, E, A]) extends FreeBracket[S, Nothing, BIOExit[E, A]]
  private[data] final case class Redeem[S[_, _], E, E1, A, B](sub: FreeBracket[S, E, A], err: E => FreeBracket[S, E1, B], suc: A => FreeBracket[S, E1, B])
    extends FreeBracket[S, E1, B]

  private[data] final case class Bracket[S[_, _], E, A0, A](
    acquire: FreeBracket[S, E, A0],
    release: A0 => FreeBracket[S, Nothing, Unit],
    use: A0 => FreeBracket[S, E, A],
  ) extends FreeBracket[S, E, A] {
    override def toString: String = s"${this.getClass.getName}[bracket=${use.getClass.getSimpleName}]"
  }
}
