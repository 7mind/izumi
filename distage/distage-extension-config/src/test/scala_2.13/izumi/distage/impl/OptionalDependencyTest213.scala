package izumi.distage.impl

import izumi.functional.bio.{BIO, BIO3, BIOApplicative, BIOApplicativeError, BIOArrow, BIOArrowChoice, BIOAsk, BIOAsync, BIOBifunctor, BIOBracket, BIOConcurrent, BIOError, BIOFork, BIOFunctor, BIOGuarantee, BIOLocal, BIOMonad, BIOMonadAsk, BIOPanic, BIOParallel, BIOPrimitives, BIOProfunctor, BIOTemporal}
import org.scalatest.wordspec.AnyWordSpec

class OptionalDependencyTest213 extends AnyWordSpec {

  "Perform exhaustive search for BIO and not find instances for ZIO / monix-bio when they're not on classpath" in {
    final class optSearch2[C[_[_, _]]] { def find[F[_, _]](implicit a: C[F] = null.asInstanceOf[C[F]]): C[F] = a }
    final class optSearch3[C[_[_, _, _]]] { def find[F[_, _, _]](implicit a: C[F] = null.asInstanceOf[C[F]]): C[F] = a }

    assert(new optSearch2[BIO].find == null)
    assert(new optSearch3[BIO3].find == null)

    assert(new optSearch2[BIOFunctor].find == null)
    assert(new optSearch2[BIOApplicative].find == null)
    assert(new optSearch2[BIOMonad].find == null)
    assert(new optSearch2[BIOBifunctor].find == null)
    assert(new optSearch2[BIOGuarantee].find == null)
    assert(new optSearch2[BIOApplicativeError].find == null)
    assert(new optSearch2[BIOError].find == null)
    assert(new optSearch2[BIOBracket].find == null)
    assert(new optSearch2[BIOPanic].find == null)
    assert(new optSearch2[BIOParallel].find == null)
    assert(new optSearch2[BIO].find == null)
    assert(new optSearch2[BIOAsync].find == null)
    assert(new optSearch2[BIOTemporal].find == null)
    assert(new optSearch2[BIOConcurrent].find == null)
    assert(new optSearch3[BIOAsk].find == null)
    assert(new optSearch3[BIOMonadAsk].find == null)
    assert(new optSearch3[BIOProfunctor].find == null)
    assert(new optSearch3[BIOArrow].find == null)
    assert(new optSearch3[BIOArrowChoice].find == null)
    assert(new optSearch3[BIOLocal].find == null)

    assert(new optSearch2[BIOFork].find == null)
    assert(new optSearch2[BIOPrimitives].find == null)
//    assert(new optSearch2[BlockingIO].find == null)  // hard to make searching this not require zio currently (`type ZIOWithBlocking` creates issue)
  }

}
