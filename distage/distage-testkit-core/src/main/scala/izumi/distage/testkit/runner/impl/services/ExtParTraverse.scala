package izumi.distage.testkit.runner.impl.services

import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.functional.quasi.QuasiIO.syntax.*

trait ExtParTraverse[F[_]] {
  def apply[A, B](
    l: Iterable[A]
  )(getParallelismGroup: A => Parallelism
  )(f: A => F[B]
  ): F[List[B]]
}

object ExtParTraverse {
  class ExtParTraverseImpl[F[_]](
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
  ) extends ExtParTraverse[F] {
    def apply[A, B](
      l: Iterable[A]
    )(getParallelismGroup: A => Parallelism
    )(f: A => F[B]
    ): F[List[B]] = {
      val sorted = l.groupBy(getParallelismGroup).toList.sortBy {
        case (Parallelism.Unlimited, _) => 1
        case (Parallelism.Fixed(_), _) => 2
        case (Parallelism.Sequential, _) => 3
      }
      F.traverse(sorted) {
        case (Parallelism.Fixed(n), l) if l.size > 1 => P.parTraverseN(n)(l)(f)
        case (Parallelism.Unlimited, l) if l.size > 1 => P.parTraverse(l)(f)
        case (_, l) => F.traverse(l)(f)
      }.map(_.flatten)
    }
  }
}
