package izumi.functional.bio.unsafe

import izumi.functional.bio.impl.BioEither
import izumi.functional.bio.{Error2, Parallel2, ParallelErrorAccumulatingOps2}
import izumi.functional.quasi.QuasiAsync
import izumi.fundamentals.platform.functional.Identity

import scala.collection.compat.{Factory, IterableOnce}

object UnsafeInstances {

  implicit def Lawless_ParallelErrorAccumulatingOpsEither: Parallel2[Either] & ParallelErrorAccumulatingOps2[Either] = Lawless_ParallelErrorAccumulatingOpsEitherImpl

  private object Lawless_ParallelErrorAccumulatingOpsEitherImpl extends Parallel2[Either] with ParallelErrorAccumulatingOps2[Either] {
    override val InnerF: Error2[Either] = BioEither

    private val idAsync: QuasiAsync[Identity] = QuasiAsync.quasiAsyncIdentity

    override def parTraverseAccumErrors[ColL[_], E, A, B](
      col: Iterable[A]
    )(f: A => Either[ColL[E], B]
    )(implicit
      buildL: Factory[E, ColL[E]],
      iterL: ColL[E] => IterableOnce[E],
    ): Either[ColL[E], List[B]] = {
      InnerF.sequenceAccumErrors(idAsync.parTraverse(col)(f))
    }
    override def parTraverseAccumErrors_[ColL[_], E, A](
      col: Iterable[A]
    )(f: A => Either[ColL[E], Unit]
    )(implicit
      buildL: Factory[E, ColL[E]],
      iterL: ColL[E] => IterableOnce[E],
    ): Either[ColL[E], Unit] =
      InnerF.sequenceAccumErrors_(idAsync.parTraverse(col)(f))

    override def parTraverse[E, A, B](l: Iterable[A])(f: A => Either[E, B]): Either[E, List[B]] = {
      InnerF.sequence(idAsync.parTraverse(l)(f))
    }

    override def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => Either[E, B]): Either[E, List[B]] = {
      InnerF.sequence(idAsync.parTraverseN(maxConcurrent)(l)(f))
    }

    override def parTraverseNCore[E, A, B](l: Iterable[A])(f: A => Either[E, B]): Either[E, List[B]] = {
      InnerF.sequence(idAsync.parTraverseN(java.lang.Runtime.getRuntime.availableProcessors() max 2)(l)(f))
    }

    override def zipWithPar[E, A, B, C](fa: Either[E, A], fb: Either[E, B])(f: (A, B) => C): Either[E, C] = {
      InnerF.map2(fa, fb)(f)
    }
  }

}
