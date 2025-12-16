package izumi.distage.testkit.runner.impl.services

import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO}

import scala.annotation.nowarn

trait ParTraverseExt[F[_]] {
  def groupedParTraverse[A, B](l: Iterable[A])(getParallelismGroup: A => Parallelism)(f: A => F[B]): F[List[B]]
  def configuredParTraverse[A, B](parallelism: Parallelism)(l: Iterable[A])(f: A => F[B]): F[List[B]]
}

object ParTraverseExt {

  @nowarn("msg=[Uu]nused import")
  final class ParTraverseExtImpl[F[_]](
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
  ) extends ParTraverseExt[F] {
    import scala.collection.compat.*

    override def groupedParTraverse[A, B](l0: Iterable[A])(getParallelismGroup: A => Parallelism)(f: A => F[B]): F[List[B]] = {
      val sorted = l0.groupBy(getParallelismGroup).toList.sortBy {
        case (Parallelism.Unlimited, _) => 1
        case (Parallelism.Fixed(_), _) => 2
        case (Parallelism.Sequential, _) => 3
      }
      F.traverse(sorted) {
        case (p, l) => configuredParTraverse(p)(l)(f)
      }.map(_.flatten)
    }

    override def configuredParTraverse[A, B](parallelism: Parallelism)(l: Iterable[A])(f: A => F[B]): F[List[B]] = {
      parallelism match {
        case Parallelism.Unlimited if l.sizeIs > 1 => P.parTraverse(l)(f)
        case Parallelism.Fixed(n) if l.sizeIs > 1 && n > 1 => P.parTraverseN(n)(l)(f)
        case _ => F.traverse(l)(f)
      }
    }

  }

}
