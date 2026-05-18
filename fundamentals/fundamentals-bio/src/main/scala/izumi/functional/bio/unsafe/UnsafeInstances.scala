package izumi.functional.bio.unsafe

import izumi.functional.bio.impl.BioEither
import izumi.functional.bio.{Error2, Parallel2, ParallelErrorAccumulatingOps2}

import scala.collection.compat.*

object UnsafeInstances {

  implicit def Lawless_ParallelErrorAccumulatingOpsEither: Parallel2[Either] & ParallelErrorAccumulatingOps2[Either] = Lawless_ParallelErrorAccumulatingOpsEitherImpl

  private object Lawless_ParallelErrorAccumulatingOpsEitherImpl extends Parallel2[Either] with ParallelErrorAccumulatingOps2[Either] {
    override val InnerF: Error2[Either] = BioEither

    override def parTraverseAccumErrors[ColL[_], E, A, B](
      col: Iterable[A]
    )(f: A => Either[ColL[E], B]
    )(implicit
      buildL: Factory[E, ColL[E]],
      iterL: ColL[E] => IterableOnce[E],
    ): Either[ColL[E], List[B]] = {
      // `Either` is synchronous; parallelism collapses to traversal. We collect successes in
      // a List builder and accumulate any errors via `iterL`, returning a single Left if any.
      val bad = buildL.newBuilder
      val good = List.newBuilder[B]
      var anyBad = false
      val it = col.iterator
      while (it.hasNext) {
        f(it.next()) match {
          case Left(es) =>
            anyBad = true
            bad ++= iterL(es)
          case Right(b) => good += b
        }
      }
      if (anyBad) Left(bad.result()) else Right(good.result())
    }
    override def parTraverseAccumErrors_[ColL[_], E, A](
      col: Iterable[A]
    )(f: A => Either[ColL[E], Unit]
    )(implicit
      buildL: Factory[E, ColL[E]],
      iterL: ColL[E] => IterableOnce[E],
    ): Either[ColL[E], Unit] = {
      val bad = buildL.newBuilder
      var anyBad = false
      val it = col.iterator
      while (it.hasNext) {
        f(it.next()) match {
          case Left(es) =>
            anyBad = true
            bad ++= iterL(es)
          case Right(()) =>
        }
      }
      if (anyBad) Left(bad.result()) else Right(())
    }

    override def parTraverse[E, A, B](l: Iterable[A])(f: A => Either[E, B]): Either[E, List[B]] = {
      InnerF.traverse(l)(f)
    }

    override def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => Either[E, B]): Either[E, List[B]] = {
      InnerF.traverse(l)(f)
    }

    override def parTraverseNCore[E, A, B](l: Iterable[A])(f: A => Either[E, B]): Either[E, List[B]] = {
      InnerF.traverse(l)(f)
    }

    override def zipWithPar[E, A, B, C](fa: Either[E, A], fb: Either[E, B])(f: (A, B) => C): Either[E, C] = {
      InnerF.map2(fa, fb)(f)
    }
  }

}
