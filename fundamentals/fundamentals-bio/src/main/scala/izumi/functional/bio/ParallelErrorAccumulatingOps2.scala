package izumi.functional.bio

import scala.collection.compat.*

trait ParallelErrorAccumulatingOps2[F[+_, +_]] extends Parallel2[F] {
  def InnerF: Error2[F]

  def parTraverseAccumErrors[ColL[_], E, A, B](
    col: Iterable[A]
  )(f: A => F[ColL[E], B]
  )(implicit buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): F[ColL[E], List[B]] = {
    implicit val F: Error2[F] = InnerF

    parTraverse(col)(F `attempt` f(_)).flatMap {
      F.traverseAccumErrors(_)(F.fromEither(_))
    }
  }

  def parTraverseAccumErrors_[ColL[_], E, A](
    col: Iterable[A]
  )(f: A => F[ColL[E], Unit]
  )(implicit buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): F[ColL[E], Unit] = {
    implicit val F: Error2[F] = InnerF

    parTraverse(col)(F `attempt` f(_)).flatMap {
      F.traverseAccumErrors_(_)(F.fromEither(_))
    }
  }
}
